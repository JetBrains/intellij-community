#import <Cocoa/Cocoa.h>

@interface ScrubberItemView : NSScrubberItemView
- (void)setImgAndText:(NSImage *)img text:(NSString *)txt;
- (void)setBackgroundSelected:(bool)selected;
@end

extern const int g_marginImgText;
extern const int g_marginBorders;