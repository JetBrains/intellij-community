package com.intellij.util.indexing;

import com.datastax.driver.core.*;
import com.intellij.openapi.application.ApplicationManager;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Function;

import com.intellij.openapi.util.Pair;

public class CassandraIndexTable {

  private final Session mySession;
  private final PreparedStatement myQueryStatement;
  private final PreparedStatement myForwardQueryStatement;

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

    mySession.execute("CREATE KEYSPACE IF NOT EXISTS indices WITH replication = {'class' : 'SimpleStrategy', 'replication_factor' : 1};");

    mySession.execute("CREATE TABLE IF NOT EXISTS indices.indices(" +
                      "index_id text, " +
                      "key blob, " +
                      "shard_id int, " +
                      "indexing_session int, " +
                      "values blob, " +
                      "PRIMARY KEY ((index_id, key), indexing_session, shard_id)) " +
                      "WITH CLUSTERING ORDER BY (indexing_session DESC)");

    mySession.execute("CREATE TABLE IF NOT EXISTS indices.forward(" +
                      "indexing_session int, " +
                      "file_id int, " +
                      "index_id text, " +
                      "values blob, " +
                      "PRIMARY KEY (file_id, index_id))");

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

    myForwardQueryStatement = mySession.prepare(new SimpleStatement("SELECT values as result FROM indices.forward WHERE file_id = ? AND index_id = ?"));
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

  private final Map<String, PreparedStatement> stmts = new HashMap<>();

  private PreparedStatement getInvertedInsertStatement(String indexId, int shardId, int sessionId) {
    return stmts.computeIfAbsent(indexId + shardId + ":" + sessionId, s ->
      mySession.prepare(new SimpleStatement("INSERT INTO indices.indices (index_id, shard_id, indexing_session, key, values) VALUES ('" +
                                            indexId +
                                            "', " +
                                            shardId +
                                            ", " +
                                            sessionId +
                                            ", ?, ?)")));
  }

  private PreparedStatement getForwardInsertStatement(String indexId, int sessionId) {
    return stmts.computeIfAbsent("forward " + indexId + ":" + sessionId, s ->
      mySession.prepare(new SimpleStatement("INSERT INTO indices.forward (index_id, indexing_session, file_id, values) VALUES ('" +
                                            indexId +
                                            "', " +
                                            sessionId +
                                            ", ?, ?)")));
  }
}