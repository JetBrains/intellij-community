// "Replace with lambda" "true"
class Test {
  {
    Comparable<String> c = (final String o)->{
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(o);
            }
        };
        return 0;
    }; 
  }
}