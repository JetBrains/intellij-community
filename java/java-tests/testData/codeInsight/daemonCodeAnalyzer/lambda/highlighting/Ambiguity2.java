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

import java.util.*;

class Test {
    interface Foo<T> {
        void foo(T t);
    }

    interface IntFoo {
        void foo(int i);
    }
    
    interface ObjectFoo<T> {
        void foo(int i);
    }
    
    interface FooList<T> {
        void foo(List<T> tList);
    }

    <T> void f(Foo<T> foo) {
        System.out.println("Foo<T>");
    }
    
    <T> void f(FooList<T> foo) {
        System.out.println("FooList<T>");
    }
    /*void f(IntFoo foo) {
        System.out.println("IntFoo");
    }*/
    
    /*void f(ObjectFoo foo) {
        System.out.println("ObjectFoo");
    }*/

    public static void main(String[] args) {
        new Test().f<error descr="Ambiguous method call: both 'Test.f(Foo<Object>)' and 'Test.f(FooList<Object>)' match">(null)</error>;
        new Test().<error descr="Cannot resolve method 'f(<lambda expression>)'">f</error>(x -> {});
    }
}

class Test1 {
    interface Foo<T> {
        void foo(T t);
    }

    interface IntFoo {
        void foo(int i);
    }
    
    interface ObjectFoo<T> {
        void foo(int i);
    }
    
    interface FooList<T> {
        void foo(List<T> tList);
    }

    <T> void f(Foo<T> foo) {
        System.out.println("Foo<T>");
    }
    
    <T> void f(FooList<T> foo) {
        System.out.println("FooList<T>");
    }
    
    void f(IntFoo foo) {
        System.out.println("IntFoo");
    }
    
    /*void f(ObjectFoo foo) {
        System.out.println("ObjectFoo");
    }*/

    public static void main(String[] args) {
        new Test1().f<error descr="Ambiguous method call: both 'Test1.f(Foo<Object>)' and 'Test1.f(FooList<Object>)' match">(null)</error>;
        new Test1().f(x -> {});
    }
}

class Test2 {
    interface Foo<T> {
        void foo(T t);
    }

    interface IntFoo {
        void foo(int i);
    }
    
    interface ObjectFoo<T> {
        void foo(int i);
    }
    
    interface FooList<T> {
        void foo(List<T> tList);
    }

    <T> void f(Foo<T> foo) {
        System.out.println("Foo<T>");
    }
    
    <T> void f(FooList<T> foo) {
        System.out.println("FooList<T>");
    }
    void f(IntFoo foo) {
        System.out.println("IntFoo");
    }
    
    void f(ObjectFoo foo) {
        System.out.println("ObjectFoo");
    }

    public static void main(String[] args) {
        new Test2().f<error descr="Ambiguous method call: both 'Test2.f(Foo<Object>)' and 'Test2.f(FooList<Object>)' match">(null)</error>;
        new Test2().f<error descr="Ambiguous method call: both 'Test2.f(IntFoo)' and 'Test2.f(ObjectFoo)' match">(x -> {})</error>;
    }
}


class Test3 {
    interface Foo<T> {
        void foo(T t);
    }

    interface IntFoo {
        void foo(int i);
    }
    
    interface ObjectFoo<T> {
        void foo(int i);
    }
    
    interface FooList<T> {
        void foo(List<T> tList);
    }

    <T> void f(Foo<T> foo) {
        System.out.println("Foo<T>");
    }
    
    <T> void f(FooList<T> foo) {
        System.out.println("FooList<T>");
    }
    /*void f(IntFoo foo) {
        System.out.println("IntFoo");
    }*/
    
    void f(ObjectFoo foo) {
        System.out.println("ObjectFoo");
    }

    public static void main(String[] args) {
        new Test3().f<error descr="Ambiguous method call: both 'Test3.f(Foo<Object>)' and 'Test3.f(FooList<Object>)' match">(null)</error>;
        new Test3().f(x -> {});
    }
}


class Test {
    interface Foo<T> {
        void foo(T t, T t1);
    }
    interface Foo1<T> {
        void foo(T t, String t1);
    }
    interface Foo2<T> {
        void foo(String t, String t1);
    }

    <T> void f(Foo<T> foo) {
        System.out.println("Foo<T>");
    }
    
    <T> void f(Foo1<T> foo) {
        System.out.println("FooList<T>");
    }
    <T> void f(Foo2<T> foo) {
        System.out.println("FooList<T>");
    }
    public static void main(String[] args) {
        new Test().f((String x, String y) -> {});
    }
}









