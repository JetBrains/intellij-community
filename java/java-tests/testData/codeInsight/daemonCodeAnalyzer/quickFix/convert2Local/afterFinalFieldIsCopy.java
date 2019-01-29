// "Convert field to local variable in method 'Test'" "true"
class Test {

    public Test(String param) {
        System.out.println(param == null ? "null" : param);
  }

}