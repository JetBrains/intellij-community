import java.util.*;
class Test {

  public void method() {
    String a = "A";
    ArrayList<String> listA = new ArrayList<String>();
    listA.add(a);

    ArrayList<String> listB = new ArrayList<String>();
    ArrayList<String> listC = new ArrayList<String>();
    listB.add("B");
    listC.add("C");
  }//ins and outs
//in: PsiLocalVariable:a
//exit: SEQUENTIAL PsiExpressionStatement
}