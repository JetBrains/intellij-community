public class CompositeAssignment {
    public static String foo(String portalName, String itemName) {
        String <caret>redirectPath = "/portals/" + portalName + "/widgets/";
        redirectPath += itemName;
        return redirectPath.toUpperCase();
    }
}