package com.siyeh.ig.fixes.logging;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.logging.LogStatementGuardedByLogConditionInspection;

/**
 * @author Bas Leijdekkers
 */
public class LogStatementGuardedByLogConditionFixTest extends IGQuickFixesTestCase {

  public void testSimple() { doTest(); }
  public void testBraces() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package java.util.logging;" +
                       "public class Logger {" +
                       "  public void fine(String msg) {}" +
                       "  public boolean isLoggable(Level level) { return true; }" +
                       "  public static Logger getLogger(String log) { return new Logger(log); }" +
                       "}");
    myFixture.enableInspections(new LogStatementGuardedByLogConditionInspection());
    myDefaultHint = InspectionGadgetsBundle.message("log.statement.guarded.by.log.condition.quickfix");
  }

  @Override
  protected String getRelativePath() {
    return "logging/log_statement_guarded_by_log_condition";
  }
}
