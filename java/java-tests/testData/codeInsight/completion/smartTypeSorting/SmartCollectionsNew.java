public abstract class Foo {

   private class Bar extends Foo {}

    {                                                                                            
        java.util.List<Foo> list;
        list.contains(new <caret>)
    }

}