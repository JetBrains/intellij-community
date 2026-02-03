public class MyAspect {

    public void foo() {
      String nameToUse = /*adjustName*/(toString())<caret>;  //todo 
    }

}
