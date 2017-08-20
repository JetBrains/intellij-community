package com.intellij.util.indexing;

import com.datastax.driver.core.*;
import com.intellij.openapi.application.ApplicationManager;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;

import com.intellij.openapi.util.Pair;

public class CassandraIndexTable {

  private final Session mySession;
  private final PreparedStatement myQueryStatement;

  public Collection<ByteBuffer> readKey(String id, int indexingSession, ByteBuffer key) {
    ResultSet result = mySession.execute(myQueryStatement.bind(indexingSession, id, key));
    Row row = result.one();
    Map<Integer, ByteBuffer> byShard = row.getMap("result", Integer.class, ByteBuffer.class);
    ByteBuffer buffer = byShard.get(1);
    return byShard.values();
  }

  public static CassandraIndexTable getInstance() {
    return ApplicationManager.getApplication().getComponent(CassandraIndexTable.class);
  }

  public CassandraIndexTable() {
    Cluster cluster = Cluster.builder()
      .addContactPoint("127.0.0.1")
      .withPort(9042)
      .build();
    mySession = cluster.connect();

    mySession.execute("CREATE KEYSPACE IF NOT EXISTS indices WITH replication = {'class' : 'SimpleStrategy', 'replication_factor' : 1};");

    mySession.execute("CREATE TABLE IF NOT EXISTS indices.indices(" +
                      "index_id text, " +
                      "key blob, " +
                      "shard_id int, " +
                      "indexing_session int, " +
                      "values blob, " +
                      "PRIMARY KEY ((index_id, key), indexing_session, shard_id)) " +
                      "WITH CLUSTERING ORDER BY (indexing_session DESC)");

    mySession.execute("CREATE OR REPLACE FUNCTION indices.uniqueShards (state map<int, blob>, shard int, val blob) " +
                      "CALLED ON NULL INPUT RETURNS map<int, blob> LANGUAGE java as " +
                      "'" +
                      "if (state == null) { " +
                      "state = new java.util.HashMap<Integer, java.nio.ByteBuffer>(); " +
                      "} " +
                      "if (state.containsKey(shard)) { " +
                      "  return state; " +
                      "} " +
                      "state.put(shard, val); " +
                      " return state; " +
                      "'");

    mySession.execute("CREATE OR REPLACE AGGREGATE indices.queryIndex (int, blob) " +
                      "SFUNC uniqueShards " +
                      "STYPE map<int, blob>");

    myQueryStatement = mySession.prepare(new SimpleStatement("SELECT queryIndex(shard_id, values) as result FROM indices.indices " +
                                                             "WHERE indexing_session <= ? AND " +
                                                             "index_id = ? AND " +
                                                             "key = ? " +
                                                             "ORDER BY indexing_session DESC"));
  }

  public void bulkInsert(String indexId, int shardId, int indexingSession, Stream<Pair<ByteBuffer, ByteBuffer>> s) {
    int chunkSize = 50 * 1024;
    List<Pair<ByteBuffer, ByteBuffer>> chunk = new ArrayList<>();
    int currentChunkBytes = 0;
    Iterator<Pair<ByteBuffer, ByteBuffer>> iterator = s.iterator();
    while (iterator.hasNext()) {
      Pair<ByteBuffer, ByteBuffer> pair = iterator.next();
      int size = pair.first.limit() - pair.first.position() + pair.second.limit() - pair.second.position();
      if (size > chunkSize) {
        insertOne(indexId, shardId, indexingSession, pair);
      } else {
        if (currentChunkBytes + size > chunkSize) {
          flushChunk(indexId, shardId, indexingSession, chunk);
          chunk.clear();
          currentChunkBytes = size;
          chunk.add(pair);
        }
        else {
          currentChunkBytes += size;
          chunk.add(pair);
        }
      }
    }
    if (!chunk.isEmpty()) {
      flushChunk(indexId, shardId, indexingSession, chunk);
    }
  }

  private final Map<String, PreparedStatement> stmts = new HashMap<>();

  private PreparedStatement getInsertStatement(String indexId, int shardId, int sessionId) {
    return stmts.computeIfAbsent(indexId + shardId + ":" + sessionId, s ->
      mySession.prepare(new SimpleStatement("INSERT INTO indices.indices (index_id, shard_id, indexing_session, key, values) VALUES ('" +
                                            indexId +
                                            "', " +
                                            shardId +
                                            ", " +
                                            sessionId +
                                            ", ?, ?)")));
  }
  private void insertOne(String indexId, int shardId, int sessionId, Pair<ByteBuffer, ByteBuffer> p) {
    mySession.execute(getInsertStatement(indexId, shardId, sessionId).bind(p.first, p.second));
  }

  private void flushChunk(String indexId, int shardId, int sessionId, List<Pair<ByteBuffer, ByteBuffer>> chunk) {
    PreparedStatement insertStmt = getInsertStatement(indexId, shardId, sessionId);

    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
    for (Pair<ByteBuffer, ByteBuffer> pair : chunk) {
      batch.add(insertStmt.bind(pair.first, pair.second));
    }
    mySession.execute(batch);
  }
}