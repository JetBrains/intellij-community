class Test1 {
    {
      Comparable<String> c = o -> {
        if (o == null) return 1;
        return -1;
        <error descr="Unreachable statement">System.out.println("Hello");</error>
      };
    }
}