import java.util.List;

class Demo {
  private static final int CONST = 5;

  public static void main(String[] args) {
    if (<warning descr="Only one array element is used">new boolean[]{true, false, false, true}[3]</warning>) {

    }
    System.out.println((new boolean[10])[3]);
    System.out.println(<warning descr="Only one list element is used">(List.of(1,2,3)).get(1)</warning>);
    System.out.println(<warning descr="Only one list element is used">(List.of(1,2,3,4,5,6,7,8,9,10,11)).get(10)</warning>);
    Integer[] integers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    System.out.println((List.of(integers)).get(0));
    System.out.println(<warning descr="Only one list element is used">(List.of(integers[0])).get(0)</warning>);
    System.out.println(<warning descr="Only one string character is used">"Hello World".charAt((10))</warning>);
    System.out.println("Hello World".charAt(11));
    System.out.println("Hello World".charAt(-1));
    System.out.println("Hello World".charAt(CONST));
  }
}