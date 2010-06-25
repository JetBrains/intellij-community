public class a extends Exception {
  private <warning descr="Private constructor 'a(java.lang.String)' is never used">a</warning>(String s) {
    super(s);
  }
}

class s extends Exception {
  private s(String s) {
    super(s);
  }
  public s create() {
    return new s("");
  }
}

class PrivateClassTest {
	private static class Test1 {
		// Complains that this constructor is never used
		private Test1 () {}

		private Test1 (String s) {
            System.out.println(s);
        }
	}

	private static class Test2 extends Test1 {
		// Complains that no default constructor is available
		public Test2 () {
        }

		// Complains that the relevant constructor has private access
		public Test2 (String s) {
			super (s);
		}
	}
    public static void main(String[] args) {
        System.out.println(new Test2());
    }

    private void <warning descr="Private method 'f(boolean, int)' is never used">f</warning>(boolean b,
               int <warning descr="Parameter 'param' is never used">param</warning>) {
       if (b) {
         f(b, param);
       }
    }
}
