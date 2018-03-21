#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

typedef void (*callback)(void);
@class TouchBar;

@interface ItemDesc : NSObject
{
    NSString * _itemId;
}
@property (retain, nonatomic) NSString * uid;
@end

@interface SpacingDesc : ItemDesc
- (id)init:(NSString *)type;
@end

@interface ButtonDesc : ItemDesc
{
    callback _action;
}
- (id)init:(callback)act;
@property (nonatomic) callback action;
@end

@interface ButtonImgDesc : ButtonDesc
{
    NSImage * _img;
}
- (id)init:(char*)praster width:(int)w height:(int)h act:(callback)act;
@property (retain, nonatomic) NSImage * img;
@end

@interface ButtonTextDesc : ButtonDesc
{
    NSString * _text;
}
- (id)init:(NSString*)text act:(callback)act;
@property (retain, nonatomic) NSString * text;
@end

@interface PopoverDesc : ItemDesc
{
    NSImage * _img;
    NSString * _text;
    TouchBar * _expandBar;
    TouchBar * _tapAndHoldBar;
}
- (id)init:(NSString *)text img:(char *)praster imgW:(int)w imgH:(int)h popoverWidth:(int)popW;
@property (retain, nonatomic) NSImage * img;
@property (retain, nonatomic) NSString * text;
@property (retain, nonatomic) TouchBar * expandBar;
@property (retain, nonatomic) TouchBar * tapHoldBar;
@property (nonatomic) int width;
@end
