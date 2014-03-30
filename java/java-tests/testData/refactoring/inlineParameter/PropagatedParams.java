class X {
    void bar(String str){
        foo(str, str.substring(0));
    }

    void foo(String str, String st<caret>r1) {
      System.out.println(str);
      System.out.println(str1);
    }
}