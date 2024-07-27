import org.<error descr="Cannot resolve symbol 'junit'">junit</error>.jupiter.api.Test;

public class  EmptyTest<caret>
{

  public static void main(String[] args) {

  }

  @<error descr="Cannot resolve symbol 'Test'">Test</error>
  public void test1() {
  }

}
