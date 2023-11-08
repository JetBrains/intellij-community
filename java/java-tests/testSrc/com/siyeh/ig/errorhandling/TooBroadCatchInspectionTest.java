package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TooBroadCatchInspectionTest extends LightJavaInspectionTestCase {

  public void testTooBroadCatchBlock() {
    doTest();
  }
  
  public void testFix() {
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
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TooBroadCatchInspection();
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/errorhandling/toobroadcatch";
  }
}