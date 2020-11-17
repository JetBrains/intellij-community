#import "ScrubberItemView.h"

//#define TRACE_TEXT_SIZES

const int g_marginImgText = 3;
const int g_marginBorders = 10;

@interface ScrubberItemView() {
    bool _isSelected;
    bool _isEnabled;
}
@property (retain, nonatomic) NSImageView * imageView;
@property (retain, nonatomic) NSTextField * textField;
@end

@implementation ScrubberItemView

- (instancetype)initWithFrame:(NSRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self != nil) {
        self.textField = [[[NSTextField alloc] initWithFrame:NSZeroRect] autorelease];
        self.imageView = [[[NSImageView alloc] initWithFrame:NSZeroRect] autorelease];

        _isSelected = false;
        _isEnabled = true;

        self.textField.font = [NSFont systemFontOfSize: 0]; // If size is 0 then macOS will give you the proper font metrics for the NSTouchBar.
        self.textField.textColor = [NSColor alternateSelectedControlTextColor];

        self.textField.alignment = NSTextAlignmentCenter;
        self.textField.cell.usesSingleLineMode = YES;

        [self addSubview:self.imageView];
        [self addSubview:self.textField];
        [self setLayoutConstraints];
    }

    return self;
}

- (void)setBackgroundSelected:(bool)selected {
    _isSelected = selected;
//    [self.view setNeedsDisplayInRect:self.bounds];
}

- (void)setEnabled:(bool)enabled {
    _isEnabled = enabled;

    self.textField.textColor = _isEnabled ? [NSColor alternateSelectedControlTextColor] : [NSColor disabledControlTextColor];
}

- (bool)isEnabled {
    return _isEnabled;
}

- (void)drawRect:(NSRect)dirtyRect {
    // NOTE: simple addSubview:NSButton (with img and text and rounded bezel style) doesn't works
    NSRect rect = NSMakeRect([self bounds].origin.x, [self bounds].origin.y, [self bounds].size.width, [self bounds].size.height);

    NSBezierPath * path = [NSBezierPath bezierPathWithRoundedRect:rect xRadius:5.0 yRadius:5.0];
    [path addClip];

    NSColor * bg = _isSelected ? [NSColor selectedControlColor] : [NSColor controlColor];
    [bg set];
    NSRectFill(rect);

    [super drawRect:dirtyRect];
}

- (void)setLayoutConstraints {
    self.imageView.translatesAutoresizingMaskIntoConstraints = NO;
    self.textField.translatesAutoresizingMaskIntoConstraints = NO;

    NSTextField * targetTextField = self.textField;
    NSImageView * targetImageView = self.imageView;

    NSDictionary * viewBindings = NSDictionaryOfVariableBindings(targetImageView, targetTextField);
    NSString * formatString = [NSString stringWithFormat:@"H:|-%d-[targetImageView]-%d-[targetTextField]-%d-|", g_marginBorders, g_marginImgText, g_marginBorders];
    NSArray * hConstraints = [NSLayoutConstraint constraintsWithVisualFormat:formatString
        options:NSLayoutFormatDirectionLeadingToTrailing
        metrics:nil
        views:viewBindings];

    formatString = @"V:|-0-[targetImageView]-0-|";
    NSArray *vConstraints = [NSLayoutConstraint constraintsWithVisualFormat:formatString
        options:NSLayoutFormatDirectionLeadingToTrailing
        metrics:nil
        views:viewBindings];

    NSLayoutConstraint *alignConstraint = [NSLayoutConstraint constraintWithItem:self.imageView
        attribute:NSLayoutAttributeCenterY
        relatedBy:NSLayoutRelationEqual
        toItem:self.textField
        attribute:NSLayoutAttributeCenterY
        multiplier:1
        constant:0];

    NSMutableArray *constraints = [NSMutableArray arrayWithArray:hConstraints];
    [constraints addObjectsFromArray:vConstraints];
    [constraints addObject:alignConstraint];

    [NSLayoutConstraint activateConstraints:constraints];
}

- (void)setImage:(NSImage *)img {
    _imageView.image = img;
}

- (void)setText:(NSString *)txt {
    if (txt == nil)
      txt = @"";
    _textField.stringValue = txt;
    [_textField sizeToFit];

#ifdef TRACE_TEXT_SIZES
    NSSize txtSize = [txt sizeWithAttributes:@{ NSFontAttributeName:_textField.font }];
    NSLog(@"TRACE: text '%@, fit rect w = %1.2f, calc text w = %1.2f, diff = %1.2f", txt, _textField.bounds.size.width, txtSize.width, _textField.bounds.size.width - txtSize.width);
#endif //TRACE_TEXT_SIZES
}

@end
