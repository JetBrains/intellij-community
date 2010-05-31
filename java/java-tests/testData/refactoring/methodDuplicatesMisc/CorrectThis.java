public class BaseClass extends JComponent {
}

class SubClass extends BaseClass {
 void a() {
  System.out.println(getLocation());
 }
}

class Util {
 public static void met<caret>hod(BaseClass base) {
  System.out.println(base.getLocation());
 }
}
