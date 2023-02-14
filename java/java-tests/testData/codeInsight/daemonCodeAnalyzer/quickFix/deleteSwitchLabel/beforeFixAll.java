// "Fix all 'Nullability and data flow problems' problems in file" "true"
class Main {
    void t() {
        int i = 5;
        switch(i) {
            case <caret>1: case 3: // Apply 'Fix all problems in the file'
                System.out.println("odd");
                break;
        }
    }
}
