class GetClassClient {
        public void use() {
                Class<? extends LocalGeneric> v1 = null;
                Class<? extends LocalGeneric<Object>> v2 = null;
                v1 = v2;
              <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends GetClassClient.LocalGeneric>>', required: 'java.lang.Class<? extends GetClassClient.LocalGeneric<java.lang.Object>>'">v2 = v1</error>;
        }

        public static class LocalGeneric<T> {
        }
}


interface Comparable<T extends Comparable<T>> {}
class List<T> {}

class Foo implements Comparable<Foo> {
  public static void main(String[] args){
    List<? extends Foo> list = null;
    List<? extends Comparable> c = null;
    c = list;
  }
}
