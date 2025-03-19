
/// Record javadoc 

record Rec(java.util.List<String> y) {
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec();
        System.out.println(rec);
    }
}