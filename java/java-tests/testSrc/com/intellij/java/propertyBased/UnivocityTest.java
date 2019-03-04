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
package com.intellij.java.propertyBased;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.Assume;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SkipSlowTestLocally
public class UnivocityTest extends AbstractApplyAndRevertTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
    MadTestingUtil.enableAllInspections(myProject, myProject);
  }

  public void testCompilabilityAfterIntentions() {
    Assume.assumeTrue("Maven import failed", JavaPsiFacade.getInstance(myProject).findClass("org.testng.Assert", GlobalSearchScope.allScope(myProject)) != null);

    initCompiler();
    PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();

    AtomicLong rebuildStamp = new AtomicLong();

    PropertyChecker.customized()
      .withIterationCount(30).checkScenarios(() -> env -> {
      long startModCount = tracker.getModificationCount();
      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      MadTestingUtil.changeAndRevert(myProject, () -> {
        env.executeCommands(Generator.constant(env1 -> {
          PsiJavaFile file = env1.generateValue(psiJavaFiles(), "Open %s in editor");
          env1.executeCommands(IntDistribution.uniform(1, 5), Generator.constant(new InvokeIntention(file, new JavaGreenIntentionPolicy())));
        }));

        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
    });
  }

  public void testRandomActivity() {
    Generator<PsiJavaFile> javaFiles = psiJavaFiles();
    PropertyChecker.customized()
      .withIterationCount(30).checkScenarios(() -> env ->
      MadTestingUtil.changeAndRevert(myProject, () ->
        env.executeCommands(Generator.constant(env1 -> {
          PsiJavaFile file = env1.generateValue(javaFiles, "Open %s in editor");

          Map<Generator<? extends MadTestingAction>, Integer> actionWeights = new HashMap<>();

          List<ActionOnFile> psiMutations = Arrays.asList(new DeleteRange(file),
                                                          new AddNullArgument(file),
                                                          new DeleteForeachInitializers(file),
                                                          new DeleteSecondArgument(file),
                                                          new MakeAllMethodsVoid(file));
          for (ActionOnFile mutation : psiMutations) {
            actionWeights.put(Generator.constant(mutation), 1);
          }

          actionWeights.put(Generator.constant(new InvokeCompletion(file, new JavaCompletionPolicy())), 1);
          actionWeights.put(Generator.constant(new RehighlightAllEditors(myProject)), 2);
          actionWeights.put(javaFiles.flatMap(f -> Generator.sampledFrom(new FilePropertiesChanged(f), new AddImportExternally(f))), 5);
          actionWeights.put(Generator.constant(new InvokeIntention(file, new JavaIntentionPolicy())), 10);

          env1.executeCommands(IntDistribution.uniform(1, 7), Generator.frequency(actionWeights));
        }))));
  }

  @Override
  protected String getTestDataPath() {
    File file = new File(PathManager.getHomePath(), "univocity-parsers");
    if (!file.exists()) {
      fail("Cannot find univocity project, execute this in project home: git clone https://github.com/JetBrains/univocity-parsers.git");
    }
    return file.getAbsolutePath();
  }

  private static class AddImportExternally extends ActionOnFile {
    AddImportExternally(PsiJavaFile file) {super(file);}

    @Override
    public void performCommand(@NotNull Environment env) {
      PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
      PsiFile file = getFile();
      if (file instanceof PsiJavaFile) {
        PsiImportList importList = ((PsiJavaFile)file).getImportList();
        if (importList != null) {
          PsiImportStatement[] statements = importList.getImportStatements();
          if (statements.length > 0) {
            int offset = statements[0].getTextRange().getStartOffset();

            String text = file.getText();
            String toInsert = "import unresolved.*;\n";
            env.logMessage("AddImportExternally: inserting " + StringUtil.escapeStringCharacters(toInsert) + " at " + file.getName() + ":" + offset);
            FileDocumentManager.getInstance().saveDocument(getDocument());

            try {
              WriteAction.run(() -> VfsUtil.saveText(getVirtualFile(), text.substring(0, offset) + toInsert + text.substring(offset)));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
    }
  }
}
