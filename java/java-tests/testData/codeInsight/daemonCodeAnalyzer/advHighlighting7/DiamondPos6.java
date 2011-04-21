/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
class Pos06 {
    static class Foo<X> {
        Foo(X x) {  }
    }

    static class DoubleFoo<X,Y> {
        DoubleFoo(X x,Y y) {  }
    }

    static class TripleFoo<X,Y,Z> {
        TripleFoo(X x,Y y,Z z) {  }
    }

    Foo<? extends Integer> fi = new Foo<>(1);
    Foo<?> fw = new Foo<>(fi);
    Foo<? extends Double> fd = new Foo<>(3.0);
    DoubleFoo<?,?> dw = new DoubleFoo<>(fi,fd);
    Foo<String> fs = new Foo<>("one");
    TripleFoo<?,?,?> tw = new TripleFoo<>(fi,fd,fs);
}
