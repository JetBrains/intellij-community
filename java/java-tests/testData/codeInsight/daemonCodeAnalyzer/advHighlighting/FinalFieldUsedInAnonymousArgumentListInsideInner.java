import java.util.Date;

class C {
  private final String s;

  C(String s) {
    this.s = s;
  }

  Object m() {
    return new Object() {
      private final Date d = new Date(s) {}; // reference to s is legal
    };
  }
}