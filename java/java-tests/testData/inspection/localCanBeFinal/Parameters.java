class Test
{
    public static void main(String[] <warning descr="Parameter 'args' can have 'final' modifier">args</warning>) {
       System.out.println(args[0]);
    }

    public static void foo(String cannotBeFinal) {
       cannotBeFinal = "";
       System.out.println(cannotBeFinal);
    }

    public static void bar(final String alreadyFinal) {
       System.out.println(alreadyFinal);
    }

    public static void bar1(final String alreadyFinal) {
    }
}

