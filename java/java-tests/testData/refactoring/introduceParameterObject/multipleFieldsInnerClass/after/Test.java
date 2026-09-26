
class Test {
  public static void main(String[] args) {
    method("John", "Doe");
  }
  
  public static void foo(Param param) {
    System.out.println("first: " + param.getFirstName());
    System.out.println("last: " + param.lastName());
      param.setFirstName("");
  }

    public static class Param {
        private String firstName;
        private final String lastName;

        public Param(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }
}