/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  void m() {
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
    JavaPsiFacade.getInstance().findClass();
  }
}

class JavaPsiFacade {
  static JavaPsiFacade getInstance() {
    return null;
  }

  PsiClass findClass() {
    return null;
  }
}

interface PsiElement {
  PsiClass getContainingClass();

  void mm();
}

interface PsiClass extends PsiElement {
}
