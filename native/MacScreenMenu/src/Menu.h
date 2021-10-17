#ifndef MACSCREENMENU_MENU_H
#define MACSCREENMENU_MENU_H

#import "MenuItem.h"

@interface Menu : MenuItem <NSMenuDelegate> {
@public
    NSMenu *nsMenu;
}

- (id)initWithPeer:(jobject)peer;
- (void)dealloc;

- (void)setTitle:(NSString *)title;
- (void)addItem:(MenuItem *)newItem;// NOTE: newItem also can be Menu (i.e. submenu)
@end

#endif //MACSCREENMENU_MENU_H
