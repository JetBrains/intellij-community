/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
public class C {
  void foo(boolean a, boolean b, boolean c) {
       boolean option1 = true;
       boolean option2 = true;
      final boolean b1 = a && b;
      if (option1) {
         if (option2) {
           if (b1 && c) {
             System.out.println("One");
           }
         }
         else {
           if (b1) {
             System.out.println("two");
           }
         }
       }
       else {
         if (b1) {
           System.out.println("three");
         }
       }
     }
  
    
}