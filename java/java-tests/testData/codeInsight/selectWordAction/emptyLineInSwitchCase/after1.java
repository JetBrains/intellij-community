class C {
    void m() {
        switch (0) {
            case 0:
                System.out.println(0);
                break;
            case 1:
<selection>                System.out.println(1);
<caret>
                break;
</selection>        }
    }
}