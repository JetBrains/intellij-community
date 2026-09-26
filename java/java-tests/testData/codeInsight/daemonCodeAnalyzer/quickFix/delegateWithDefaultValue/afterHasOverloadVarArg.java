// "Generate overloaded method with default parameter values" "true"
class Test {
    void method() {
        method((String[]) null);
    }

    void method(String... s) {}
  
  void method(Integer i) {}
}