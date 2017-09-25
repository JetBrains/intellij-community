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
package com.intellij.java.codeInsight.daemon.quickFix;


import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  StreamApiMigrationInspectionTest.AddAllTest.class,
  StreamApiMigrationInspectionTest.AfterAllActionsTest.class,
  StreamApiMigrationInspectionTest.AllMatchTest.class,
  StreamApiMigrationInspectionTest.AnyMatchTest.class,
  StreamApiMigrationInspectionTest.BufferedReaderTest.class,
  StreamApiMigrationInspectionTest.CollectTest.class,
  StreamApiMigrationInspectionTest.ContinueTest.class,
  StreamApiMigrationInspectionTest.CountTest.class,
  StreamApiMigrationInspectionTest.FilterTest.class,
  StreamApiMigrationInspectionTest.FindFirstTest.class,
  StreamApiMigrationInspectionTest.FlatMapFirstTest.class,
  StreamApiMigrationInspectionTest.ForeachFirstTest.class,
  StreamApiMigrationInspectionTest.JoiningTest.class,
  StreamApiMigrationInspectionTest.LimitTest.class,
  StreamApiMigrationInspectionTest.MinMaxTest.class,
  StreamApiMigrationInspectionTest.NoneMatchTest.class,
  StreamApiMigrationInspectionTest.OtherTest.class,
  StreamApiMigrationInspectionTest.ReductionTest.class,
  StreamApiMigrationInspectionTest.SumTest.class,
  StreamApiMigrationInspectionTest.TakeWhileTest.class,
})
public class StreamApiMigrationInspectionTest {
  public static abstract class StreamApiMigrationInspectionBaseTest extends LightQuickFixParameterizedTestCase {
    @NotNull
    @Override
    protected LocalInspectionTool[] configureLocalInspectionTools() {
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

    public void test() { doAllTests(); }

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

  public static class ForeachFirstTest extends StreamApiMigrationInspectionBaseTest {
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

  public static class SumTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "sum";
    }
  }

  public static class TakeWhileTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "takeWhile";
    }
  }
}