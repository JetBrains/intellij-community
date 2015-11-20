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
import java.util.List;
class Test1 {
    
    interface I<X> {
        X foo(List<String> list);
    }
    
    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}
    
    {
        bar(x -> x);
        bar1(x -> x);

        I<Object> lO =  x->x;
        bar2("", lO);

        I<String> lS =  x-><error descr="Bad return type in lambda expression: List<String> cannot be converted to String">x</error>;
        bar2("", lS);

        bar2("", x -> x);

        bar3(x -> x, "");

        int ixc = 42;
        bar(x -> {
              if (ixc == 2) return "aaa";
              return x;
           });
        bar(x -> {
              if (ixc == 2) return x;
              return x;
           });
    }
}


class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}

    {
        bar(x -> x);
        bar1(x -> x);
        bar2(1, <error descr="Bad return type in lambda expression: List<Integer> cannot be converted to Integer">x -> x</error>);
        bar2("", <error descr="Bad return type in lambda expression: List<String> cannot be converted to String">x -> x</error>);
        bar3(<error descr="Bad return type in lambda expression: List<String> cannot be converted to String">x -> x</error>, "");
    }
}

class Test3 {

    interface I<X> {
        List<X> foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
    static <T> void bar1(I<T> i){}
    static <T> void bar2(T t, I<T> i){}
    static <T> void bar3(I<T> i, T t){}

    {
        bar(x -> x);
        bar1(x -> x);
        bar2(1, x -> x);
        bar2("", x -> x);

        bar3(x -> x, "");
    }
}
