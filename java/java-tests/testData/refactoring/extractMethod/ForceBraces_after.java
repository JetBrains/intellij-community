class Xtract {
    void me(int i) {
        if (newMethod(i)) {
            return;
        }

        System.out.println("i: " + i);
    }

    private boolean newMethod(int i) {
        if (i ==10) {
            return true;
        }
        return false;
    }
}