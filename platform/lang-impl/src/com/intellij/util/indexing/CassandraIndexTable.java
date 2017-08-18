package com.intellij.util.indexing;

import com.datastax.driver.core.*;
import com.intellij.openapi.application.ApplicationManager;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;

import com.intellij.openapi.util.Pair;

public class CassandraIndexTable {

  private final Session mySession;

  public static CassandraIndexTable getInstance() {
    return ApplicationManager.getApplication().getComponent(CassandraIndexTable.class);
  }

  public CassandraIndexTable() {
    Cluster cluster = Cluster.builder()
      .addContactPoint("127.0.0.1")
      .withPort(9042)
      .build();
    mySession = cluster.connect();
  }

  public void bulkInsert(String indexId, int shardId, int indexingSession, Stream<Pair<ByteBuffer, ByteBuffer>> s) {
    int chunkSize = 5 * 1024;
    List<Pair<ByteBuffer, ByteBuffer>> chunk = new ArrayList<>();
    int currentChunkBytes = 0;
    Iterator<Pair<ByteBuffer, ByteBuffer>> iterator = s.iterator();
    while (iterator.hasNext()) {
      Pair<ByteBuffer, ByteBuffer> pair = iterator.next();
      int size = pair.first.limit() - pair.first.position() + pair.second.limit() - pair.second.position();
      if (size > chunkSize) {
        insertOne(indexId, shardId, indexingSession, pair);
        //System.out.println("Chunk is too large indexId: " + indexId + " size: " + size + " max allowed: " + chunkSize);
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

    BatchStatement batch = new BatchStatement();
    for (Pair<ByteBuffer, ByteBuffer> pair : chunk) {
      batch.add(insertStmt.bind(pair.first, pair.second));
    }
    mySession.execute(batch);
  }
}