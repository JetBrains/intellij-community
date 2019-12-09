#import <Cocoa/Cocoa.h>

@interface ScrubberItemView : NSScrubberItemView
- (void)setImage:(NSImage *)img;
- (void)setText:(NSString *)txt;
- (void)setBackgroundSelected:(bool)selected;
- (void)setEnabled:(bool)enabled;
- (bool)isEnabled;
@end

extern const int g_marginImgText;
extern const int g_marginBorders;