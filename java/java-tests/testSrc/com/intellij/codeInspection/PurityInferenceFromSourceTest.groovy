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
package com.intellij.codeInspection

import com.intellij.codeInspection.dataFlow.PurityInference
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class PurityInferenceFromSourceTest extends LightCodeInsightFixtureTestCase {

  public void "test getter"() {
    assertPure true, """
Object getField() {
  return field;
}
"""
  }

  public void "test setter"() {
    assertPure false, """
void setField(String s) {
  field = s;
}
"""
  }

  public void "test unknown"() {
    assertPure false, """
int random() {
  launchMissiles();
  return 2;
}
"""
  }

  public void "test print"() {
    assertPure false, """
int random() {
  System.out.println("hello");
  return 2;
}
"""
  }

  public void "test local var assignment"() {
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

  public void "test local array var assignment"() {
    assertPure true, """
int[] randomArray() {
  int[] i = new int[0];
  i[0] = random();
  return i;
}
int random() { return 2; }
"""
  }

  public void "test field array assignment"() {
    assertPure false, """
int[] randomArray() {
  i[0] = random();
  return i;
}
int random() { return 2; }
  int[] i = new int[0];
"""
  }

  public void "test use explicit pure contract"() {
    assertPure true, """
int method() {
  return smthPure();
}
@org.jetbrains.annotations.Contract(pure=true) native int smthPure();
"""
  }

  public void "test don't analyze more than one call"() {
    assertPure false, """
int method() {
  return smthPure(smthPure2());
}
int smthPure(int i) { return i; }
int smthPure2() { return 42; }
"""
  }

  public void "test don't analyze void methods"() {
    assertPure false, """
void method() {
  smthPure();
}
int smthPure() { return 3; }
"""
  }

  public void "test don't analyze methods without returns"() {
    assertPure false, """
Object method() {
    smthPure();
}
int smthPure() { return 3; }
"""
  }

  public void "test don't analyze constructors"() {
    assertPure false, """
public Foo() {
}
"""
  }

  public void "test calling constructor with side effects"() {
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

  private void assertPure(boolean expected, String classBody) {
    def clazz = myFixture.addClass("final class Foo { $classBody }")
    assert expected == PurityInference.inferPurity(clazz.methods[0])
  }

}
