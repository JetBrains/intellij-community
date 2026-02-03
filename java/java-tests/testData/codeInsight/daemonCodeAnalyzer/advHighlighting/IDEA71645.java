class Component {
    void addChild( Component b ) {
    }
}

class Panel extends Component {
    public Panel( Component pNode ) {
    }

    Panel() {
    }
}

class VerticalPanel extends Panel {
    public VerticalPanel() {
        super( new Component() {{
            addChild( new Panel() );
        }} );
    }
}
