/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.Queryable;
import com.intellij.pom.java.LanguageLevel;

import java.util.Comparator;

public class IdeaTestUtil extends PlatformTestUtil {
  public static Comparator<AbstractTreeNode> createComparator(final Queryable.PrintInfo printInfo) {
    return new Comparator<AbstractTreeNode>() {
      @Override
      public int compare(final AbstractTreeNode o1, final AbstractTreeNode o2) {
        String displayText1 = o1.toTestString(printInfo);
        String displayText2 = o2.toTestString(printInfo);
        return displayText1.compareTo(displayText2);
      }
    };
  }

  /**
   * Use {@link #createComparator(com.intellij.openapi.ui.Queryable.PrintInfo)} instead.
   */
  @Deprecated
  public static final Comparator<AbstractTreeNode> DEFAULT_COMPARATOR = new Comparator<AbstractTreeNode>() {
    @Override
    public int compare(AbstractTreeNode o1, AbstractTreeNode o2) {
      String displayText1 = o1.getTestPresentation();
      String displayText2 = o2.getTestPresentation();
      return displayText1.compareTo(displayText2);
    }
  };

  public static void main(String[] args) {
    printDetectedPerformanceTimings();
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
  public static void printDetectedPerformanceTimings() {
    System.out.println("Etalon timing: " + ETALON_TIMING);
    System.out.println("This machine timing: " + Timings.MACHINE_TIMING);
  }

  public static void withLevel(final Module module, final LanguageLevel level, final Runnable r) {
    final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(module.getProject());
    final LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtension.getInstance(module);

    final LanguageLevel projectLevel = projectExt.getLanguageLevel();
    final LanguageLevel moduleLevel = moduleExt.getLanguageLevel();
    try {
      projectExt.setLanguageLevel(level);
      setModuleLanguageLevel(level, moduleExt);
      r.run();
    }
    finally {
      setModuleLanguageLevel(moduleLevel, moduleExt);
      projectExt.setLanguageLevel(projectLevel);
    }
  }

  private static void setModuleLanguageLevel(final LanguageLevel level, final LanguageLevelModuleExtension moduleExt) {
    final LanguageLevelModuleExtension modifiable = (LanguageLevelModuleExtension)moduleExt.getModifiableModel(true);
    modifiable.setLanguageLevel(level);
    modifiable.commit();
  }
}
