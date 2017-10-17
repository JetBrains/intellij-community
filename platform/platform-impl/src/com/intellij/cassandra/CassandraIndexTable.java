/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cassandra;

import com.datastax.driver.core.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsSource;
import com.intellij.openapi.vfs.newvfs.persistent.IFSRecords;
import com.intellij.util.containers.ContainerUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CassandraIndexTable {

  private final Session mySession;
  private final PreparedStatement myQueryStatement;
  private final PreparedStatement myForwardQueryStatement;
    private final PreparedStatement myRootsQueryStatement;
  private final PreparedStatement myLoadRecordsQueryStatement;
  private final PreparedStatement myTreeQueryStatement;
  private final PreparedStatement myMountsQueryStatement;
  private final PreparedStatement myContentsQueryStatement;
  private final PreparedStatement myAttrQueryStatement;
  private final PreparedStatement myQueryShardsStatement;

  public List<IFSRecords.RootRecord> getRoots(List<Integer> shards) {
    return mySession.execute(myRootsQueryStatement.bind(shards))
      .all()
      .stream()
      .map(row -> new IFSRecords.RootRecord(row.getInt("file_id"),
                                            row.getString("url")))
      .collect(Collectors.toList());
  }

  public List<FSRecordsSource.RecordInfo> loadFSRecords(List<FSRecordsSource.RecordId> ids) {
    return ids.stream().collect(Collectors.groupingBy(id -> id.shardId,
                                                      Collectors.mapping(x -> x.fileId, Collectors.toList())))
      .entrySet()
      .stream()
      .flatMap(entry -> mySession.execute(myLoadRecordsQueryStatement.bind(entry.getKey(), entry.getValue()))
               .all().stream()
               .map(r -> new FSRecordsSource.RecordInfo(r.getInt("file_id"),
                                                        r.getString("name"),
                                                        r.getLong("timestamp"),
                                                        r.getLong("length"),
                                                        r.getInt("flags"),
                                                        r.getBytes("content_hash"))))
      .collect(Collectors.toList());
  }

  public ByteBuffer getContent(ByteBuffer contentHash) {
    Row one = mySession.execute(myContentsQueryStatement.bind(contentHash)).one();
    return one == null ? null : one.getBytes("content");
  }

  public ByteBuffer readAttr(FSRecordsSource.RecordId recordId, String attrId, int version) {
    Row r = mySession.execute(myAttrQueryStatement.bind(recordId.shardId, recordId.fileId, attrId)).one();
    return r == null ? null : r.getBytes("value");
  }

  public List<Integer> getTree(int shardId) {
    Row one = mySession.execute(myTreeQueryStatement.bind(shardId)).one();
    if (one == null) {
      return ContainerUtil.emptyList();
    }
    return one.getList("tree", Integer.class);
  }

  public List<FSRecordsSource.RecordId> getMountedChildren(FSRecordsSource.RecordId recordId) {
    return mySession.execute(myMountsQueryStatement.bind(recordId.fileId, recordId.shardId))
      .all()
      .stream()
      .map(row -> new FSRecordsSource.RecordId(row.getInt("child_file_id"),
                                               row.getInt("child_shard_id")))
      .collect(Collectors.toList());
  }

  public List<Integer> getShardIds(int fileId) {
    return mySession.execute(myQueryShardsStatement.bind(fileId))
      .all().stream()
      .map(x-> x.getInt("shard_id"))
      .collect(Collectors.toList());
  }

  public static CassandraIndexTable getInstance() {
    return ApplicationManager.getApplication().getComponent(CassandraIndexTable.class);
  }

  public Collection<ByteBuffer> readKey(String id, int indexingSession, ByteBuffer key) {
    ResultSet result = mySession.execute(myQueryStatement.bind(indexingSession, id, key));
    Row row = result.one();
    Map<Integer, ByteBuffer> byShard = row.getMap("result", Integer.class, ByteBuffer.class);
    return byShard.values();
  }

  public ByteBuffer readForward(String indexId, int fileId) {
    ResultSet result = mySession.execute(myForwardQueryStatement.bind(fileId, indexId));
    Row row = result.one();
    if (row == null)
      return null;
    return row.getBytes("result");
  }

  public CassandraIndexTable() {
    Cluster cluster = Cluster.builder()
      .addContactPoint("127.0.0.1")
      .withPort(9042)
      .build();

    mySession = cluster.connect();

    myLoadRecordsQueryStatement = mySession.prepare("SELECT * from indices.fs_records WHERE shard_id = ? AND file_id in ?");

    myContentsQueryStatement = mySession.prepare("SELECT content from indices.fs_contents WHERE content_hash = ?");

    myAttrQueryStatement = mySession.prepare("SELECT value FROM indices.fs_attrs WHERE shard_id = ? AND file_id = ? AND attribute = ?");

    myTreeQueryStatement = mySession.prepare("SELECT tree FROM indices.fs_trees WHERE shard_id = ?");

    myQueryShardsStatement = mySession.prepare("SELECT shard_id FROM indices.fs_shards WHERE file_id = ?");

    myMountsQueryStatement = mySession.prepare("SELECT * FROM indices.fs_mounts WHERE parent_file_id = ? AND parent_shard_id = ?");


    //---------------------

    myQueryStatement = mySession.prepare("SELECT queryIndex(shard_id, values) as result FROM indices.indices " +
                                         "WHERE indexing_session <= ? AND " +
                                         "index_id = ? AND " +
                                         "key = ? " +
                                         "ORDER BY indexing_session DESC");


    myForwardQueryStatement = mySession.prepare("SELECT values as result FROM indices.forward WHERE file_id = ? AND index_id = ?");

    myRootsQueryStatement = mySession.prepare("SELECT url, file_id FROM indices.fs_roots WHERE shard_id = ? AND indexing_session = ?");





  }

  public void publish(int indexingSession, int shardId, int maxId) {
    mySession
      .execute("INSERT INTO indices.indexing_sessions" +
                      " (shard_id, indexing_session, max_file_id) " +
               "VALUES (?,         ?,                ?)",
                        shardId,   indexingSession,  maxId);
  }

  public <X> void bulks(Stream<X> s, Consumer<List<X>> insertBulk, Consumer<X> insertOne, Function<X, Integer> measure) {
    int chunkSize = 200 * 1024;
    List<X> chunk = new ArrayList<>();
    int currentChunkBytes = 0;
    Iterator<X> iterator = s.iterator();
    while (iterator.hasNext()) {
      X x = iterator.next();
      int size = measure.apply(x);
      if (size > chunkSize) {
        insertOne.accept(x);
      }
      else {
        if (currentChunkBytes + size > chunkSize) {
          insertBulk.accept(chunk);
          chunk.clear();
          currentChunkBytes = size;
          chunk.add(x);
        }
        else {
          currentChunkBytes += size;
          chunk.add(x);
        }
      }
    }
    if (!chunk.isEmpty()) {
      insertBulk.accept(chunk);
    }
  }

  public void bulkInsert(String indexId, int shardId, int indexingSession, Stream<Pair<ByteBuffer, ByteBuffer>> s) {
    PreparedStatement insertStmt = getInvertedInsertStatement(indexId, shardId, indexingSession);
    bulks(s,
          chunk -> {
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (Pair<ByteBuffer, ByteBuffer> pair : chunk) {
              batch.add(insertStmt.bind(pair.first, pair.second));
            }
            mySession.execute(batch);
          },
          p -> mySession.execute(insertStmt.bind(p.first, p.second)),
          pair -> pair.first.limit() - pair.first.position() + pair.second.limit() - pair.second.position());
  }

  public void bulkInsertForward(String indexId, int indexingSession, Stream<Pair<Integer, ByteBuffer>> s) {
    PreparedStatement insertStmt = getForwardInsertStatement(indexId, indexingSession);
    bulks(s,
          chunk -> {
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (Pair<Integer, ByteBuffer> pair : chunk) {
              batch.add(insertStmt.bind(pair.first, pair.second));
            }
            mySession.execute(batch);
          },
          pair -> mySession.execute(insertStmt.bind(pair.first, pair.second)),
          pair -> pair.second.limit() - pair.second.position() + 4);
  }

  public static class AttrInfo {
    int fileId;

    String attribute;
    ByteBuffer value;
    public AttrInfo(int fileId, String attribute, ByteBuffer value) {
      this.fileId = fileId;
      this.attribute = attribute;
      this.value = value;

    }
  }

  public void bulkInsertAttrs(int shardId, Stream<AttrInfo> s) {

    PreparedStatement stmt = getAttributeInsertStatement(shardId);
    bulks(s,
          infos -> {
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (AttrInfo info : infos) {
              batch.add(stmt.bind(info.fileId, info.attribute, info.value));
            }
            mySession.execute(batch);
          },
          info -> mySession.execute(stmt.bind(info.fileId, info.attribute, info.value)),
          info -> 4 + info.attribute.length() + info.value.limit() - info.value.position());
  }

  private final Map<String, PreparedStatement> stmts = new HashMap<>();

  private PreparedStatement getAttributeInsertStatement(int shardId) {
    return stmts.computeIfAbsent("attr_insert " + shardId, s ->
    mySession.prepare(new SimpleStatement("INSERT INTO indices.attrs (shard_id, file_id, attribute, value) VALUES (" +
                                          shardId + ", ?, ?, ?)")));
  }

  private PreparedStatement getInvertedInsertStatement(String indexId, int shardId, int sessionId) {
    return stmts.computeIfAbsent("inverted_insert " + indexId + shardId + ":" + sessionId, s ->
      mySession.prepare(new SimpleStatement("INSERT INTO indices.indices (index_id, shard_id, indexing_session, key, values) VALUES ('" +
                                            indexId +
                                            "', " +
                                            shardId +
                                            ", " +
                                            sessionId +
                                            ", ?, ?)")));
  }

  private PreparedStatement getForwardInsertStatement(String indexId, int sessionId) {
    return stmts.computeIfAbsent("forward_insert " + indexId + ":" + sessionId, s ->
      mySession.prepare(new SimpleStatement("INSERT INTO indices.forward (index_id, indexing_session, file_id, values) VALUES ('" +
                                            indexId +
                                            "', " +
                                            sessionId +
                                            ", ?, ?)")));
  }
}