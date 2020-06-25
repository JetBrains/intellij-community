// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.GenerateGetterHandler
import com.intellij.codeInsight.generation.GenerateSetterHandler
import com.intellij.codeInsight.generation.SetterTemplatesManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
/**
 * @author peter
 */

@CompileStatic
class GenerateGetterSetterTest extends LightJavaCodeInsightFixtureTestCase {

  void "test don't strip is of non-boolean fields"() {
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

  void "test strip is of boolean fields"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    boolean isStateForceMailField;
    boolean isic;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    boolean isStateForceMailField;
    boolean isic;

    public boolean isStateForceMailField() {
        return isStateForceMailField;
    }

    public boolean isIsic() {
        return isic;
    }
}
'''
  }

  void "test strip is of boolean fields setter"() {
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

  void "test builder setter template"() {
    myFixture.configureByText 'a.java', '''
class X<T extends String> {
   T field;
   
   <caret>
}
'''
    try {
      SetterTemplatesManager.instance.state.defaultTemplateName = "Builder"
      generateSetter()
      myFixture.checkResult '''
class X<T extends String> {
   T field;

    public X<T> setField(T field) {
        this.field = field;
        return this;
    }
}
'''
    }
    finally {
      SetterTemplatesManager.instance.state.defaultTemplateName = null
    }
  }

  void "test strip field prefix"() {
    def settings = JavaCodeStyleSettings.getInstance(getProject())
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

  void "test qualified this"() {
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

  void "test nullable stuff"() {
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
    new GenerateGetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(
        ClassMember[] members,
        boolean allowEmptySelection,
        boolean copyJavadocCheckbox,
        Project project,
        @Nullable Editor editor) {
        return members
      }
    }.invoke(project, myFixture.editor, myFixture.file)
    UIUtil.dispatchAllInvocationEvents()
  }

  void "test static or this setter with same name parameter"() {
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

  void "test invoke between comment and method"() {
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class)
    myFixture.configureByText 'a.java', '''
class Foo {
  int a;
  //comment
 <caret> void foo() {}
}'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
  int a;

    public int getA() {
        return this.a;
    }

    //comment
  void foo() {}
}'''
  }
  
  void "test record accessor"() {
    myFixture.configureByText('a.java', '''
record Point(int x, int y) {
  <caret>
}
''')
    generateGetter()
    myFixture.checkResult('''
record Point(int x, int y) {
    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }
}
''')
  }

  private void generateSetter() {
    new GenerateSetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(
        ClassMember[] members,
        boolean allowEmptySelection,
        boolean copyJavadocCheckbox,
        Project project,
        @Nullable Editor editor) {
        return members
      }
    }.invoke(project, myFixture.editor, myFixture.file)
    UIUtil.dispatchAllInvocationEvents()
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15
  }
}
