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
package com.intellij.java.codeInspection

import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class PurityInferenceFromSourceTest extends LightCodeInsightFixtureTestCase {

  void "test getter"() {
    assertPure true, """
Object getField() {
  return field;
}
"""
  }

  void "test setter"() {
    assertPure false, """
void setField(String s) {
  field = s;
}
"""
  }

  void "test unknown"() {
    assertPure false, """
int random() {
  launchMissiles();
  return 2;
}
"""
  }

  void "test print"() {
    assertPure false, """
int random() {
  System.out.println("hello");
  return 2;
}
"""
  }

  void "test local var assignment"() {
    assertPure true, """
int random(boolean b) {
  int i = 4;
  if (b) {
    i = 2;
  } else {
    i++;
  }
  return i;
}
"""
  }

  void "test local array var assignment"() {
    assertPure true, """
int[] randomArray() {
  int[] i = new int[0];
  i[0] = random();
  return i;
}
int random() { return 2; }
"""
  }

  void "test field array assignment"() {
    assertPure false, """
int[] randomArray() {
  i[0] = random();
  return i;
}
int random() { return 2; }
  int[] i = new int[0];
"""
  }

  void "test field array assignment as local var"() {
    assertPure false, """
int[] randomArray() {
  int[] local = i;
  local[0] = random();
  return local;
}
int random() { return 2; }
  int[] i = new int[0];
"""
  }

  void "test use explicit pure contract"() {
    assertPure true, """
int method() {
  return smthPure();
}
@org.jetbrains.annotations.Contract(pure=true) native int smthPure();
"""
  }

  void "test don't analyze more than one call"() {
    assertPure false, """
int method() {
  return smthPure(smthPure2());
}
int smthPure(int i) { return i; }
int smthPure2() { return 42; }
"""
  }

  void "test don't analyze void methods"() {
    assertPure false, """
void method() {
  smthPure();
}
int smthPure() { return 3; }
"""
  }

  void "test don't analyze methods without returns"() {
    assertPure false, """
Object method() {
    smthPure();
}
int smthPure() { return 3; }
"""
  }

  void "test don't analyze constructors"() {
    assertPure false, """
public Foo() {
}
"""
  }

  void "test calling constructor with side effects"() {
    assertPure false, """
    Object newExample() {
        return new Example1();
    }

    private static int created = 0;

    Example1() {
        created++;
    }
    """
  }

  void "test anonymous class initializer"() {
    assertPure false, """
    Object smth() {
        return new I(){{ created++; }};
    }

    private static int created = 0;
    
    interface I {}
    """
  }

  void "test simple anonymous class creation"() {
    assertPure true, """
    Object smth() {
        return new I(){};
    }
    
    interface I {}
    """
  }

  void "test anonymous class with arguments"() {
    assertPure false, """
    Object smth() {
        return new I(unknown()){};
    }
    
    class I {
      I(int a) {}
    }
    """
  }

  void "test class with impure initializer creation"() {
    assertPure false, """
    Object smth() {
        return new I(42);
    }
    
    class I {
      I(int answer) {}
      {
        launchMissiles();
      }
    }
    """
  }

  void "test delegate to a method calling local class constructor"() {
    myFixture.addClass("""
class Another { 
  static Object method() {
    class LocalClass {
      LocalClass() { launchMissiles(); }
    }
    return new LocalClass();
  } 
}
  """)
    assertPure false, """
    Object smth() {
        return Another.method();
    }
    """
  }

  void "test plain field read"() {
    assertPure true, """
int x;

int get() {
  return x;
}
"""
  }

  void "test volatile field read"() {
    assertPure false, """
volatile int x;

int get() {
  return x;
}
"""
  }

  private void assertPure(boolean expected, String classBody) {
    def clazz = myFixture.addClass("final class Foo { $classBody }")
    assert !((PsiFileImpl) clazz.containingFile).contentsLoaded
    def purity = JavaSourceInference.inferPurity((PsiMethodImpl)clazz.methods[0])
    assert !((PsiFileImpl) clazz.containingFile).contentsLoaded
    assert expected == purity
  }

}
