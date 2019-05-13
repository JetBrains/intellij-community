import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class Main {

  public static void main(String[] args) {
    Collection children = new ArrayList<>();
    List<String>  actualNodes = <warning descr="Unchecked assignment: 'java.util.ArrayList' to 'java.util.List<java.lang.String>'"><warning descr="Unchecked call to 'ArrayList(Collection<? extends E>)' as a member of raw type 'java.util.ArrayList'">new ArrayList<>(children)</warning></warning>;
    System.out.println(actualNodes);
  }
}
