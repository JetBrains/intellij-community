// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;


import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  StreamApiMigrationInspectionTestSuite.AddAllTest.class,
  StreamApiMigrationInspectionTestSuite.AfterAllActionsTest.class,
  StreamApiMigrationInspectionTestSuite.AllMatchTest.class,
  StreamApiMigrationInspectionTestSuite.AnyMatchTest.class,
  StreamApiMigrationInspectionTestSuite.BufferedReaderTest.class,
  StreamApiMigrationInspectionTestSuite.CollectTest.class,
  StreamApiMigrationInspectionTestSuite.ContinueTest.class,
  StreamApiMigrationInspectionTestSuite.CountTest.class,
  StreamApiMigrationInspectionTestSuite.FilterTest.class,
  StreamApiMigrationInspectionTestSuite.FindFirstTest.class,
  StreamApiMigrationInspectionTestSuite.FlatMapFirstTest.class,
  StreamApiMigrationInspectionTestSuite.ForEachTest.class,
  StreamApiMigrationInspectionTestSuite.JoiningTest.class,
  StreamApiMigrationInspectionTestSuite.LimitTest.class,
  StreamApiMigrationInspectionTestSuite.MinMaxTest.class,
  StreamApiMigrationInspectionTestSuite.NoneMatchTest.class,
  StreamApiMigrationInspectionTestSuite.OtherTest.class,
  StreamApiMigrationInspectionTestSuite.ReductionTest.class,
  StreamApiMigrationInspectionTestSuite.SummingTest.class,
  StreamApiMigrationInspectionTestSuite.Java9Test.class,
  StreamApiMigrationInspectionTestSuite.Java10Test.class,
  StreamApiMigrationInspectionTestSuite.Java14Test.class,
})
public class StreamApiMigrationInspectionTestSuite {
  public static abstract class StreamApiMigrationInspectionBaseTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      StreamApiMigrationInspection inspection = new StreamApiMigrationInspection();
      inspection.SUGGEST_FOREACH = true;
      return new LocalInspectionTool[]{
        inspection
      };
    }

    @Override
    protected LanguageLevel getDefaultLanguageLevel() {
      return LanguageLevel.JDK_1_8;
    }

      @Override
    protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) throws Exception {
      ((IntentionManagerImpl)IntentionManager.getInstance())
        .withDisabledIntentions(() -> super.doAction(actionHint, testFullPath, testName));
    }

    abstract String getFolder();

    @Override
    protected String getBasePath() {
      return "/codeInsight/daemonCodeAnalyzer/quickFix/streamApiMigration/" + getFolder();
    }
  }

  public static class AddAllTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "addAll";
    }
  }

  public static class AfterAllActionsTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "afterAllActions";
    }
  }

  public static class AllMatchTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "allMatch";
    }
  }

  public static class AnyMatchTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "anyMatch";
    }
  }

  public static class BufferedReaderTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "bufferedReader";
    }
  }

  public static class CollectTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "collect";
    }
  }

  public static class ContinueTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "continue";
    }
  }

  public static class CountTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "count";
    }
  }

  public static class FilterTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "filter";
    }
  }

  public static class FindFirstTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "findFirst";
    }
  }

  public static class FlatMapFirstTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "flatMap";
    }
  }

  public static class ForEachTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "foreach";
    }
  }


  public static class JoiningTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "joining";
    }
  }

  public static class LimitTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "limit";
    }
  }

  public static class MinMaxTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "minMax";
    }
  }

  public static class NoneMatchTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "noneMatch";
    }
  }

  public static class OtherTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "other";
    }
  }

  public static class ReductionTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "reduce";
    }
  }

  public static class SummingTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "sum";
    }
  }

  public static class Java9Test extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "java9";
    }
  }

  public static class Java10Test extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "java10";
    }
  }

  public static class Java14Test extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "java14";
    }
  }

  public static class Java19Test extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "java19";
    }
  }
}