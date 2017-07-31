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
  StreamApiMigrationInspectionTest.AllMatchStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.AnyMatchStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.BufferedReaderStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.CollectStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.ContinueStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.FilterStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.FindFirstStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.FlatMapFirstStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.ForeachFirstStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.LimitStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.MinMaxStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.NoneMatchStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.OtherStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.ReductionOperationStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.SumOperationStreamApiMigrationTest.class,
  StreamApiMigrationInspectionTest.TakeWhileOperationStreamApiMigrationTest.class,
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

    public void test() throws Exception { doAllTests(); }

    abstract String getFolder();

    @Override
    protected String getBasePath() {
      return "/codeInsight/daemonCodeAnalyzer/quickFix/streamApiMigration/" + getFolder();
    }
  }

  public static class AddAllStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "addAll";
    }
  }

  public static class AllMatchStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "allMatch";
    }
  }

  public static class AnyMatchStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "anyMatch";
    }
  }

  public static class BufferedReaderStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "bufferedReader";
    }
  }

  public static class CollectStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "collect";
    }
  }

  public static class ContinueStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "continue";
    }
  }

  public static class FilterStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "filter";
    }
  }

  public static class FindFirstStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "findFirst";
    }
  }

  public static class FlatMapFirstStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "flatMap";
    }
  }

  public static class ForeachFirstStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "foreach";
    }
  }

  public static class LimitStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "limit";
    }
  }

  public static class MinMaxStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "minMax";
    }
  }

  public static class NoneMatchStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "noneMatch";
    }
  }

  public static class OtherStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "other";
    }
  }

  public static class ReductionOperationStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "reduce";
    }
  }

  public static class SumOperationStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "sum";
    }
  }

  public static class TakeWhileOperationStreamApiMigrationTest extends StreamApiMigrationInspectionBaseTest {
    @Override
    String getFolder() {
      return "takeWhile";
    }
  }
}