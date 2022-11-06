// "Fix all 'Constant values' problems in file" "true"
import java.util.ArrayList;
import java.util.List;

class Mutant {
    List<String> types = new ArrayList<>();

    void consider(String type, boolean unmodifiable) {
        if (unmodifiable) {
            if(Math.random() > 0.5) {
                System.out.println("1");
            }
        } else {
            types.add(type);
            System.out.println("2");
        }
        if (unmodifiable) {
            if(Math.random() > 0.5) {
                System.out.println("1");
            }
        } else {
            types.add(type);
        }
        if (unmodifiable) {
            if(Math.random() > 0.5) {
                System.out.println("1");
            }
        } else {
            types.add(type);
        }
        if (unmodifiable)
            System.out.println("1");
        else {
            types.add(type);
            System.out.println("2");
        }
        // Cannot extract side effect in the middle
        if (unmodifiable || Math.random() > 0.5)
            System.out.println("1");
        else
            System.out.println("2");
        types.add(type);
        if (unmodifiable)
            System.out.println("1");
        else
            System.out.println("2");

        System.out.println("types = " + types);
    }

    public static void main(String[] args) {
        new Mutant().consider("A", false);
    }
}