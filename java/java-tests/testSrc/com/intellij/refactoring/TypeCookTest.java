/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 22.07.2003
 * Time: 22:46:44
 * To change this template use Options | File Templates.
 */

public class TypeCookTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  public String getTestRoot() {
    return "/refactoring/typeCook/";
  }

  public void testT01() throws Exception {
    start();
  }

  public void testT02() throws Exception {
    start();
  }

  public void testT03() throws Exception {
    start();
  }

  public void testT04() throws Exception {
    start();
  }

  public void testT05() throws Exception {
    start();
  }

  public void testT06() throws Exception {
    start();
  }

  public void testT07() throws Exception {
    start();
  }

  public void testT08() throws Exception {
    start();
  }

  public void testT09() throws Exception {
    start();
  }

  public void testT10() throws Exception {
    start();
  }

  public void testT11() throws Exception {
    start();
  }

  public void testT12() throws Exception {
    start();
  }


  public void testT13() throws Exception {
    start();
  }

  public void testT14() throws Exception {
    start();
  }

  public void testT15() throws Exception {
    start();
  }

  public void testT16() throws Exception {
    start();
  }

  public void testT17() throws Exception {
    start();
  }

  public void testT18() throws Exception {
    start();
  }

  public void testT19() throws Exception {
    start();
  }

  public void testT20() throws Exception {
    start();
  }

  public void testT21() throws Exception {
    start();
  }

  public void testT22() throws Exception {
    start();
  }

  public void testT23() throws Exception {
    start();
  }

  public void testT24() throws Exception {
    start();
  }

  public void testT25() throws Exception {
    start();
  }

  public void testT26() throws Exception {
    start();
  }

  public void testT27() throws Exception {
    start();
  }

  public void testT28() throws Exception {
    start();
  }

  public void testT29() throws Exception {
    start();
  }

  public void testT30() throws Exception {
    start();
  }

  public void testT31() throws Exception {
    start();
  }

  // Waits for correct NCA...
  //public void testT32() throws Exception {
  // start();
  //}

  public void testT33() throws Exception {
    start();
  }

  public void testT34() throws Exception {
    start();
  }

  public void testT35() throws Exception {
    start();
  }

  public void testT36() throws Exception {
    start();
  }

  public void testT37() throws Exception {
    start();
  }

  public void testT38() throws Exception {
    start();
  }

  public void testT39() throws Exception {
    start();
  }

  public void testT40() throws Exception {
    start();
  }

  public void testT41() throws Exception {
    start();
  }

  public void testT42() throws Exception {
    start();
  }

  public void testT43() throws Exception {
    start();
  }

  public void testT44() throws Exception {
    start();
  }

  public void testT45() throws Exception {
    start();
  }

  public void testT46() throws Exception {
    start();
  }

  public void testT47() throws Exception {
    start();
  }

  public void testT48() throws Exception {
    start();
  }

  public void testT49() throws Exception {
    start();
  }

  public void testT50() throws Exception {
    start();
  }

  public void testT51() throws Exception {
    start();
  }

  public void testT52() throws Exception {
    start();
  }

  public void testT53() throws Exception {
    start();
  }

  public void testT54() throws Exception {
    start();
  }

  public void testT55() throws Exception {
    start();
  }

  public void testT56() throws Exception {
    start();
  }

  public void testT57() throws Exception {
    start();
  }

  public void testT58() throws Exception {
    start();
  }

  public void testT59() throws Exception {
    start();
  }

  public void testT60() throws Exception {
    start();
  }

  public void testT61() throws Exception {
    start();
  }

  public void testT62() throws Exception {
    start();
  }

  public void testT63() throws Exception {
    start();
  }

  public void testT64() throws Exception {
    start();
  }

  public void testT65() throws Exception {
    start();
  }

  public void testT66() throws Exception {
    start();
  }

  public void testT67() throws Exception {
    start();
  }

  public void testT68() throws Exception {
    start();
  }

  public void testT69() throws Exception {
    start();
  }

  public void testT70() throws Exception {
    start();
  }

  public void testT71() throws Exception {
    start();
  }

  public void testT72() throws Exception {
    start();
  }

  public void testT73() throws Exception {
    start();
  }

  public void testT74() throws Exception {
    start();
  }

  public void testT75() throws Exception {
    start();
  }

  public void testT76() throws Exception {
    start();
  }

  public void testT77() throws Exception {
    start();
  }

  public void testT78() throws Exception {
    start();
  }

  public void testT79() throws Exception {
    start();
  }

  public void testT80() throws Exception {
    start();
  }

  // Too conservative.... Waiting for better times
  //public void testT81() throws Exception {
  //  start();
  //}

  public void testT82() throws Exception {
    start();
  }

  public void testT83() throws Exception {
    start();
  }

  public void testT84() throws Exception {
    start();
  }

  public void testT85() throws Exception {
    start();
  }

  public void testT86() throws Exception {
    start();
  }

  public void testT87() throws Exception {
    start();
  }

  public void testT88() throws Exception {
    start();
  }

  public void testT89() throws Exception {
    start();
  }

  public void testT90() throws Exception {
    start();
  }

  public void testT91() throws Exception {
    start();
  }

  public void testT92() throws Exception {
    start();
  }

  public void testT93() throws Exception {
    start();
  }

  public void testT94() throws Exception {
    start();
  }

  public void testT95() throws Exception {
    start();
  }

  public void testT96() throws Exception {
    start();
  }

  public void testT97() throws Exception {
    start();
  }

  // Wrong: inner class
  //public void testT98() throws Exception {
  //  start();
  //}

  // Wrong: anonymous
  //public void testT99() throws Exception {
  //  start();
  //}

  public void testT100() throws Exception {
    start();
  }

  public void testT101() throws Exception {
    start();
  }

  public void testT102() throws Exception {
    start();
  }

  public void testT103() throws Exception {
    start();
  }

  public void testT104() throws Exception {
    start();
  }

  public void testT105() throws Exception {
    start();
  }

  public void testT106() throws Exception {
    start();
  }

  public void testT107() throws Exception {
    start();
  }

  public void testT108() throws Exception {
    start();
  }

  public void testT109() throws Exception {
    start();
  }

  public void testT110() throws Exception {
    start();
  }

  public void testT111() throws Exception {
    start();
  }

  public void testT112() throws Exception {
    start();
  }

  public void testT113() throws Exception {
    start();
  }

  public void testT114() throws Exception {
    start();
  }

  public void testT115() throws Exception {
    start();
  }

  public void testT116() throws Exception {
    start();
  }

  public void testT117() throws Exception {
    start();
  }

  public void testT118() throws Exception {
    start();
  }

  public void testT119() throws Exception {
    start();
  }

  public void testT120() throws Exception {
    start();
  }

  public void testT121() throws Exception {
    start();
  }

  //public void testT122() throws Exception {
  //  start();
  //}

  public void testT123() throws Exception {
    start();
  }

  public void testT124() throws Exception {
    start();
  }

  public void testT125() throws Exception {
    start();
  }

  public void testT126() throws Exception {
    start();
  }

  public void testT127() throws Exception {
    start();
  }

  public void testT128() throws Exception {
    start();
  }

  public void testT129() throws Exception {
    start();
  }

  public void testT130() throws Exception {
    start();
  }

  public void testT131() throws Exception {
    start();
  }

  public void testT132() throws Exception {
    start();
  }

  public void testT133() throws Exception {
    start();
  }

  public void testT134() throws Exception {
    start();
  }

  public void testT135() throws Exception {
    start();
  }

  public void testT136() throws Exception {
    start();
  }

  public void testT137() throws Exception {
    start();
  }

  //public void testT138() throws Exception {
  //   start();
  // }

  public void testT139() throws Exception {
    start();
  }

  public void testT140() throws Exception {
    start();
  }

  //public void testT141() throws Exception {
  //  start();
  //}
  //
  //public void testT142() throws Exception {
  //  start();
  //}
  //
  //public void testT143() throws Exception {
  //    start();
  //}
  //
  //public void testT144() throws Exception {
  //      start();
  //}
  //
  //public void testT145() throws Exception {
  //      start();
  //}

  public void testT146() throws Exception {
        start();
  }

  public void testT147() throws Exception {
        start();
  }

  public void testT148() throws Exception {
        start();
  }

  public void testT149() throws Exception {
        start(true);
  }

  public void testT150() throws Exception {
        start();
  }

  public void testT151() throws Exception {
        start();
  }

  public void testT152() throws Exception {
        start();
  }

  public void testConvertToDiamond() throws Exception {
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel oldLevel = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      start();
    }
    finally {
      levelProjectExtension.setLanguageLevel(oldLevel);
    }
  }

  public void start() throws Exception {
    start(false);
  }

  public void start(final boolean cookObjects) throws Exception {
    doTest((rootDir, rootAfter) -> this.performAction("Test", rootDir.getName(), cookObjects));
  }

  private void performAction(String className, String rootDir, final boolean cookObjects) throws Exception {
    PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));

    assertNotNull("Class " + className + " not found", aClass);

    SystemBuilder b = new SystemBuilder(myPsiManager.getProject(),
                                        new Settings() {
                                          @Override
                                          public boolean dropObsoleteCasts() {
                                            return true;
                                          }

                                          @Override
                                          public boolean preserveRawArrays() {
                                            return false;
                                          }

                                          @Override
                                          public boolean leaveObjectParameterizedTypesRaw() {
                                            return false;
                                          }

                                          @Override
                                          public boolean exhaustive() {
                                            return false;
                                          }

                                          @Override
                                          public boolean cookObjects(){
                                            return cookObjects;
                                          }

                                          @Override
                                          public boolean cookToWildcards(){
                                            return false;
                                          }
                                        });

    final ReductionSystem commonSystem = b.build(aClass);

    //System.out.println("System built:\n" + commonSystem);

    final ReductionSystem[] systems = commonSystem.isolate();

    //System.out.println("Systems isolated:\n" + commonSystem);

    ReductionSystem system = null;

    for (ReductionSystem s : systems) {
      if (s != null && system == null) {
        //System.out.println(s);
        system = s;
      }
    }

    Binding binding = null;

    if (system != null) {
      final ResolverTree tree = new ResolverTree(system);

      tree.resolve();

      binding = tree.getBestSolution();
    }

    String itemRepr = system != null ? system.dumpString() : commonSystem.dumpString();// d.resultString();
    doStuff(rootDir, itemRepr, className + ".items");
    itemRepr = system != null ? system.dumpResult(binding) : commonSystem.dumpString(); //d.resultString();

    doStuff(rootDir, itemRepr, className + ".1.items");
  }

  private void doStuff(String rootDir, String itemRepr, String itemName) throws FileNotFoundException {
    String patternName = getTestDataPath() + getTestRoot() + getTestName(true) + "/after/" + itemName;

    File patternFile = new File(patternName);

    PrintWriter writer;
    if (!patternFile.exists()) {
      writer = new PrintWriter(new FileOutputStream(patternFile));
      try {
        writer.print(itemRepr);
      }
      finally {
        writer.close();
      }

      System.out.println("Pattern not found, file " + patternName + " created.");

      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(patternFile);
    }

    File graFile = new File(FileUtil.getTempDirectory() + File.separator + rootDir + File.separator + itemName);

    writer = new PrintWriter(new FileOutputStream(graFile));
    try {
      writer.print(itemRepr);
    }
    finally {
      writer.close();
    }



    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(graFile);
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
