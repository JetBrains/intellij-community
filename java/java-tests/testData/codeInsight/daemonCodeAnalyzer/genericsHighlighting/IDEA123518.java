import java.util.*;

class sTest {

  public void shouldCallListConstructor(){
    List<String>stringList=new ArrayList<String>();
    ClassUnderTest<Date> cut=new ClassUnderTest<>(stringList);
  }

  private class ClassUnderTest<T extends Date> {

    public String constructorString;
    private ClassUnderTest(List<T>stringList) {
      constructorString="Using List Constructor";
    }

    private ClassUnderTest(Iterable<String> iterables) {
      constructorString="Using Iterables Constructor";
    }
  }
}