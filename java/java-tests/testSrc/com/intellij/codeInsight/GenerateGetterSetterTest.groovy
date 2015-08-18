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
package com.intellij.codeInsight
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.GenerateGetterHandler
import com.intellij.codeInsight.generation.GenerateSetterHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection
import org.jetbrains.annotations.Nullable
/**
 * @author peter
 */
class GenerateGetterSetterTest extends LightCodeInsightFixtureTestCase {

  public void "test don't strip is of non-boolean fields"() {
    myFixture.addClass('class YesNoRAMField {}')
    myFixture.configureByText 'a.java', '''
class Foo {
    YesNoRAMField isStateForceMailField;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    YesNoRAMField isStateForceMailField;

    public YesNoRAMField getIsStateForceMailField() {
        return isStateForceMailField;
    }
}
'''
  }
  
  public void "test strip is of boolean fields"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    boolean isStateForceMailField;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    boolean isStateForceMailField;

    public boolean isStateForceMailField() {
        return isStateForceMailField;
    }
}
'''
  } 
  
  public void "test strip is of boolean fields setter"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    boolean isStateForceMailField;

    <caret>
}
'''
    generateSetter()
    myFixture.checkResult '''
class Foo {
    boolean isStateForceMailField;

    public void setStateForceMailField(boolean stateForceMailField) {
        isStateForceMailField = stateForceMailField;
    }
}
'''
  }

  public void "test strip field prefix"() {
    def settings = CodeStyleSettingsManager.getInstance(getProject()).currentSettings
    String oldPrefix = settings.FIELD_NAME_PREFIX
    try {
      settings.FIELD_NAME_PREFIX = "my"
      myFixture.configureByText 'a.java', '''
  class Foo {
      String myName;

      <caret>
  }
  '''
      generateGetter()
      myFixture.checkResult '''
  class Foo {
      String myName;

      public String getName() {
          return myName;
      }
  }
  '''
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix
    }
  }

  public void "test qualified this"() {
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class)
    myFixture.configureByText 'a.java', '''
class Foo {
    boolean isStateForceMailField;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    boolean isStateForceMailField;

    public boolean isStateForceMailField() {
        return this.isStateForceMailField;
    }
}
'''
  }

  public void "test nullable stuff"() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface NotNull {}")
    myFixture.configureByText 'a.java', '''
class Foo {
    @org.jetbrains.annotations.NotNull
    private String myName;

    <caret>
}
'''
    generateGetter()
    generateSetter()
    myFixture.checkResult '''import org.jetbrains.annotations.NotNull;

class Foo {
    @org.jetbrains.annotations.NotNull
    private String myName;

    public void setMyName(@NotNull String myName) {
        this.myName = myName;
    }

    @NotNull
    public String getMyName() {
    
        return myName;
    }
}
'''
  }

  private void generateGetter() {
    WriteCommandAction.runWriteCommandAction(getProject(), {
    new GenerateGetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(
        ClassMember[] members,
        boolean allowEmptySelection,
        boolean copyJavadocCheckbox,
        Project project,
        @Nullable @Nullable Editor editor) {
        return members
      }
    }.invoke(project, myFixture.editor, myFixture.file)
    })
    UIUtil.dispatchAllInvocationEvents()
  }

  public void "test static or this setter with same name parameter"() {
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class)
    myFixture.configureByText 'a.java', '''
class Foo {
    static int p;
    int f;

    <caret>
}
'''
    generateSetter()
    myFixture.checkResult '''
class Foo {
    static int p;
    int f;

    public static void setP(int p) {
        Foo.p = p;
    }

    public void setF(int f) {
        this.f = f;
    }
}
'''
  }
  
  private void generateSetter() {
    WriteCommandAction.runWriteCommandAction(getProject(), {
    new GenerateSetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(
        ClassMember[] members,
        boolean allowEmptySelection,
        boolean copyJavadocCheckbox,
        Project project,
        @Nullable @Nullable Editor editor) {
        return members
      }
    }.invoke(project, myFixture.editor, myFixture.file)
    })
    UIUtil.dispatchAllInvocationEvents()
  }
}
