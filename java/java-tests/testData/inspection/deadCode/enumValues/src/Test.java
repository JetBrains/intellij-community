public class UnusedDeclBug {

    public static enum Concern {
        // These fields are used!  Just because I don't mention them by name
        // doesn't mean they aren't used!
        // IDEA tells me I need: @SuppressWarnings({"UnusedDeclaration"})
        LOW,
        MEDIUM,
        HIGH;
    };

    public static void main(String[] args) {
        System.out.println("Concerns are:");

        // Invoking Concern.values() should count as using all the fields in the
        // enum.
        for (Concern concern : Concern.values()) {
            System.out.print("\t");
            System.out.println(concern);
        } // end for
    }
}