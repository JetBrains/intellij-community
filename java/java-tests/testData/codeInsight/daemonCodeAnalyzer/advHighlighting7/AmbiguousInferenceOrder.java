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
package pck;

class Key<K>{}

class C {
     public <T> void putCopyableUserData(Key<T> key, T value) {
     }
}

interface D {
  <T> void putCopyableUserData(Key<T> key, T value);
}

class B extends C implements D {}

class A {
  private static final Key<Integer> INDENT_INFO = new Key<Integer>();
  
  public static void foo(B b, int oldIndentation) {
    b.putCopyableUserData(INDENT_INFO, oldIndentation >= 0 ? oldIndentation : null);
  }
}
