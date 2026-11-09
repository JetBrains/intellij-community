package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class TooBroadCatchInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new TooBroadCatchInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/errorhandling/toobroadcatch");

  @Test
  public void testTooBroadCatchBlock() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testFix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("X.java", """
      import java.io.*;
      
      class X {
          public void foo(){
                  try{
                      if(bar()){
                          throw new FileNotFoundException();
                      } else{
                          throw new EOFException();
                      }
                  } catch(FileNotFoundException e){
                      e.printStackTrace();
                  } catch(<caret>IOException e){
                      e.printStackTrace();
                  }
              }
      }
      """);
    IntentionAction intention = myFixture.findSingleIntention("Add 'catch' clause for 'java.io.EOFException'");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResult("""
        import java.io.*;
                                  
        class X {
            public void foo(){
                    try{
                        if(bar()){
                            throw new FileNotFoundException();
                        } else{
                            throw new EOFException();
                        }
                    } catch(FileNotFoundException e){
                        e.printStackTrace();
                    } catch (EOFException e) {
                        <selection><caret>throw new RuntimeException(e);</selection>
                    } catch(IOException e){
                        e.printStackTrace();
                    }
                }
        }
        """);
    });
  }
}