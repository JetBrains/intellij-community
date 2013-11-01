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
public class TestIndex {

  public void statMethod(PsiMethodCallExpression e) {
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
    e.resolveMethod().getContainingClass().getManager();
  }
}
interface PsiManager {

}
interface PsiElement {
  PsiManager getManager();
}
interface PsiClass extends PsiElement {
}
interface PsiMethod extends PsiElement {
  PsiClass getContainingClass();
}
interface PsiMethodCallExpression extends PsiElement {
  PsiMethod resolveMethod();
}
