class One {
    static boolean truth = true;
    static void important() {
        System.out.println(1);
    }
}
class A extends One {
    Two() {
        important();
        System.out.println(truth);
    }
}