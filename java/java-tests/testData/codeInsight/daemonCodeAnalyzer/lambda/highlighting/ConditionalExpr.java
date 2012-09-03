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
class Test {
  public interface I {
    int m();
  }

  public interface I1 {
    int m(int y);
  }

  {
    boolean flag = true;
    I i =  flag ? (() -> 123)   : (() -> 222);
    I i1 =  flag ? (<error descr="Missing return value">() -> {}</error>)   : (() -> 222);
    Object i2 =  flag ? (<error descr="Target type of a lambda conversion must be an interface">() -> 42</error>)   : (<error descr="Target type of a lambda conversion must be an interface">() -> 222</error>);
    <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test.I'">I i3 =  flag ? ((x) -> 42)   : (() -> 222);</error>
    I i4 =  flag ? (() -> 42) : new I() {
      @Override
      public int m() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
      }
    };
  }
}

