package com.intellij.database.extractors;

import com.intellij.database.extensions.ExtensionScriptsUtil;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static com.intellij.database.extensions.DataExtractorBindings.DATABASE_DIALECT;
import static com.intellij.database.extensions.DataExtractorBindings.DATABASE_TABLE;

public class NoDbScriptDataExtractor extends ScriptDataExtractor {
  public NoDbScriptDataExtractor(@Nullable Project project,
                                 @NotNull Path scriptFile,
                                 @NotNull IdeScriptEngine engine,
                                 @NotNull ObjectFormatter objectFormatter,
                                 boolean isAggregator,
                                 boolean supportsText) {
    super(project, scriptFile, engine, objectFormatter, isAggregator, supportsText);
    ExtensionScriptsUtil.setBindings(engine)
      .bind(DATABASE_TABLE, null)
      .bind(DATABASE_DIALECT, new MockDatabaseDialect());
  }

  public static class MockDatabaseDialect {
    private final MockDbms myDbms = new MockDbms();

    public MockDbms getDbms() {
      return myDbms;
    }
  }

  public static class MockDbms {
    public boolean isOracle() { return false; }
    public boolean isMysql() { return false; }
    public boolean isPostgres() { return false; }
    public boolean isBigQuery() { return false; }
    public boolean isRedshift() { return false; }
    public boolean isGreenplum() { return false; }
    public boolean isVertica() {return false; }
    public boolean isMicrosoft() { return false; }
    public boolean isSybase() { return false; }
    public boolean isDb2() { return false; }
    public boolean isHsqldb() { return false; }
    public boolean isH2() { return false; }
    public boolean isDerby() { return false; }
    public boolean isSqlite() { return false; }
    public boolean isExasol() { return false; }
    public boolean isClickHouse() { return false; }
    public boolean isCassandra() { return false; }
    public boolean isHive() { return false; }
    public boolean isSpark() { return false; }
    public boolean isSnowflake() { return false; }
    public boolean isMongo() { return false; }
    public boolean isCouchbase() { return false; }
    public boolean isCockroach() { return false; }
    public boolean isRedis() { return false; }

    public boolean isTransactSql() { return false; }
    public boolean isDocumentOriented() { return false; }
  }
}
