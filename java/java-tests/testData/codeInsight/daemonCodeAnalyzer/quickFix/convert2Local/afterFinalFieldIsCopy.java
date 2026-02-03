// "Convert field to local variable in constructor" "true-preview"
class Test {

    public Test(String param) {
        System.out.println(param == null ? "null" : param);
  }

}