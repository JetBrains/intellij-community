// "Convert field to local variable in constructor" "true-preview"
class Test {

    public Test(String param) {
    field = param;
    System.out.println(field == null ? "null" : field);
  }

}