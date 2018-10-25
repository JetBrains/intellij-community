// "Fix all 'Constant conditions & exceptions' problems in file" "true"
class Main {
    void t() {
        int i = 5;
        switch(i) {
            c<caret>ase 1: case 3: // Apply 'Fix all problems in the file'
                System.out.println("odd");
                break;
        }
    }
}
