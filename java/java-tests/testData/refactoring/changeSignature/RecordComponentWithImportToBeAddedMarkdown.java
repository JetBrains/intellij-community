
/// Record javadoc 

record R<caret>ec() {
}

class Use {
    public static void main(String[] args) {
        Rec rec = new Rec();
        System.out.println(rec);
    }
}