#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "ScrubberItemView.h"
#import "JTypes.h"
#import "Utils.h"

static const NSUserInterfaceItemIdentifier g_scrubberItemIdentifier = @"scrubberItem";
static const int g_heightOfTouchBar = 30;
static const int g_interItemSpacings = 5;

@interface ScrubberItem : NSObject
@property (retain, nonatomic) NSString * text;
@property (retain, nonatomic) NSImage * img;
@property (nonatomic) int index;
@property (nonatomic) int positionInsideScrubber;
@property (nonatomic) bool visible;
@property (nonatomic) bool enabled;
@end

@implementation ScrubberItem
@end

@interface NSScrubberContainer : NSCustomTouchBarItem<NSScrubberDataSource, NSScrubberDelegate, NSScrubberFlowLayoutDelegate>
{
    ScrubberItemView * _lastSelected;
}
@property (retain, nonatomic) NSMutableArray * itemsCache;
@property (retain, nonatomic) NSMutableArray * visibleItems;
@property (nonatomic) executeScrubberItem delegate;
@property (nonatomic) updateScrubberCache updateCache;
@end

@implementation NSScrubberContainer

- (NSInteger)numberOfItemsForScrubber:(NSScrubber *)scrubber {
    // NOTE: called from AppKit
    nstrace(@"scrubber [%@]: items count %lu, visible %lu", self.identifier, self.itemsCache.count, self.visibleItems.count);
    return self.visibleItems.count;
}

- (NSScrubberItemView *)scrubber:(NSScrubber *)scrubber viewForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
    nstrace(@"scrubber [%@]: create viewForItemAtIndex %lu", self.identifier, itemIndex);
    ScrubberItemView *itemView = [scrubber makeItemWithIdentifier:g_scrubberItemIdentifier owner:nil];

    ScrubberItem * itemData = [self.visibleItems objectAtIndex:itemIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: null item-data at index %lu", self.identifier, itemIndex);
        return nil;
    }

    [itemView setImgAndText:itemData.img text:itemData.text];
    [itemView setEnabled:itemData.enabled];

    if (itemIndex == self.visibleItems.count - 1)
        (*self.updateCache)();
    return itemView;
}

- (NSSize)scrubber:(NSScrubber *)scrubber layout:(NSScrubberFlowLayout *)layout sizeForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
    ScrubberItem * itemData = [self.visibleItems objectAtIndex:itemIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: null item-data at index %lu", self.identifier, itemIndex);
        return NSMakeSize(0, 0);
    }

    NSFont * font = [NSFont systemFontOfSize:0]; // Specify a system font size of 0 to automatically use the appropriate size.
    const int imgW = itemData.img != nil ? itemData.img.size.width : 0;
    NSSize txtSize = itemData.text != nil ? [itemData.text sizeWithAttributes:@{ NSFontAttributeName:font }] : NSMakeSize(0, 0);

    CGFloat width = txtSize.width + imgW + 2*g_marginBorders + g_marginImgText + 13/*empiric diff for textfield paddings*/; // TODO: get rid of empiric, use size obtained from NSTextField
    nstrace(@"scrubber [%@]: sizeForItemAtIndex %d: txt='%@', iconW=%d, txt size = %1.2f, %1.2f, result width = %1.2f, font = %@", self.identifier, itemIndex, itemData.text, imgW, txtSize.width, txtSize.height, width, font);
    return NSMakeSize(width, g_heightOfTouchBar);
}

- (void)scrubber:(NSScrubber *)scrubber didSelectItemAtIndex:(NSInteger)selectedIndex {
    ScrubberItem * itemData = [self.visibleItems objectAtIndex:selectedIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: called didSelectItemAtIndex %lu, but item-data at this index is null", self.identifier, selectedIndex);
        return;
    }

    NSScrubberItemView * view = [scrubber itemViewForItemAtIndex:selectedIndex];
    if (view == nil) {
        nserror(@"scrubber [%@]: called didSelectItemAtIndex %lu, but item-view at this index is null", self.identifier, selectedIndex);
        return;
    }

    if (_lastSelected != nil)
        [_lastSelected setBackgroundSelected:false];

    if (((ScrubberItemView *)view).isEnabled) {
        _lastSelected = (ScrubberItemView *) view;
        [_lastSelected setBackgroundSelected:true];

        nstrace(@"scrubber [%@]: perform action of scrubber item at %lu", self.identifier, selectedIndex);
        (*_delegate)(itemData.index);
    }
}

@end

static int _fillCache(NSMutableArray * cache, NSMutableArray * visibleItems, void* items, int byteCount) {
    const int prevCacheSize = cache.count;
    const int prevVisibleSize = visibleItems.count;
    const char * p = items;
    const int itemsCount = *((int*)p);
    p += 4;
    nstrace(@"items count = %d, bytes = %d", itemsCount, byteCount);
    for (int c = 0; c < itemsCount; ++c) {
        ScrubberItem * si = [[[ScrubberItem alloc] init] autorelease];
        const int txtLen = *((int*)p);
        p += 4;
        si.text = txtLen == 0 ? [NSString stringWithUTF8String:""] : [NSString stringWithUTF8String:p];
        p += txtLen + 1;
        //NSLog(@"\t len=%d, txt=%@, offset=%d", txtLen, si.text, p - (char*)items);

        const int w = *((int*)p);
        p += 4;
        const int h = *((int*)p);
        p += 4;
        //NSLog(@"\t w=%d, h=%d", w, h);

        si.img = w <= 0 || h <= 0 ? nil : createImgFrom4ByteRGBA((const unsigned char *)p, w, h);
        si.index = c + prevCacheSize;
        si.positionInsideScrubber = c + prevVisibleSize;
        si.visible = true;
        si.enabled = true;
        [cache addObject:si];
        [visibleItems addObject:si];

        p += w*h*4;
        //NSLog(@"\t offset=%d", p - (char*)items);
    }
}

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
id createScrubber(const char* uid, int itemWidth, executeScrubberItem delegate, updateScrubberCache updater, void* packedItems, int byteCount) {
    NSString * nsid = [NSString stringWithUTF8String:uid];
    nstrace(@"create scrubber [%@] (thread: %@)", nsid, [NSThread currentThread]);

    NSScrubberContainer * scrubberItem = [[NSScrubberContainer alloc] initWithIdentifier:nsid]; // create non-autorelease object to be owned by java-wrapper
    scrubberItem.itemsCache = [[[NSMutableArray alloc] initWithCapacity:10/*empiric average popup items count*/] autorelease];
    scrubberItem.visibleItems = [[[NSMutableArray alloc] initWithCapacity:10/*empiric average popup items count*/] autorelease];
    scrubberItem.delegate = delegate;
    scrubberItem.updateCache = updater;

    _fillCache(scrubberItem.itemsCache, scrubberItem.visibleItems, packedItems, byteCount);

    NSScrubber *scrubber = [[[NSScrubber alloc] initWithFrame:NSMakeRect(0, 0, itemWidth, g_heightOfTouchBar)] autorelease];

    scrubber.delegate = scrubberItem;
    scrubber.dataSource = scrubberItem;

    [scrubber registerClass:[ScrubberItemView class] forItemIdentifier:g_scrubberItemIdentifier];

    scrubber.selectedIndex = -1;

    NSScrubberFlowLayout *scrubberLayout = [[[NSScrubberFlowLayout alloc] init] autorelease];
    scrubberLayout.itemSpacing = g_interItemSpacings;
    [scrubberLayout invalidateLayout];
    scrubber.scrubberLayout = scrubberLayout;

    scrubber.mode = NSScrubberModeFree;
    scrubber.showsArrowButtons = NO;
    scrubber.showsAdditionalContentIndicators = YES;
    scrubber.selectionOverlayStyle = nil;
    [scrubber.widthAnchor constraintGreaterThanOrEqualToConstant:itemWidth].active = YES;

    scrubberItem.view = scrubber;
    return scrubberItem;
}

// NOTE: called from AppKit (when show last cached item and need update cache with new items)
void appendScrubberItems(id scrubObj, void* packedItems, int byteCount) {
    NSScrubberContainer *container = scrubObj;
    nstrace(@"scrubber [%@]: called appendScrubberItems", container.identifier);

    const int visibleItemsCountPrev = container.visibleItems.count;
    _fillCache(container.itemsCache, container.visibleItems, packedItems, byteCount);

    dispatch_async(dispatch_get_main_queue(), ^{
        NSIndexSet *indexSet = [NSIndexSet indexSetWithIndexesInRange:NSMakeRange(visibleItemsCountPrev, container.visibleItems.count - visibleItemsCountPrev)];
        [container.view insertItemsAtIndexes:indexSet];
    });
}

// NOTE: called from EDT (when update UI)
void enableScrubberItems(id scrubObj, void* itemIndices, int count, bool enabled) {
    NSScrubberContainer *container = scrubObj;
    NSScrubber *scrubber = container.view;
    const int sizeInBytes = sizeof(int)*count;
    int *indices = malloc(sizeInBytes);
    memcpy(indices, itemIndices, sizeInBytes);

    dispatch_async(dispatch_get_main_queue(), ^{
        for (int c = 0; c < count; ++c) {
            ScrubberItem * itemData = [container.itemsCache objectAtIndex:indices[c]];
            if (itemData == nil) {
                nserror(@"scrubber [%@]: called enableScrubberItem %d, but item-data at this index is null", container.identifier, indices[c]);
                continue;
            }
            itemData.enabled = enabled;
            if (itemData.positionInsideScrubber < 0) {
                nstrace(@"scrubber [%@]: called enableScrubberItem %d, but item is hidden", container.identifier, indices[c]);
                continue;
            }

            NSScrubberItemView *view = [scrubber itemViewForItemAtIndex:itemData.positionInsideScrubber];
            if (view == nil) {
                nstrace(@"scrubber [%@]: called enableScrubberItem %d, but item-view at this index is null", container.identifier, indices[c]);
                continue;
            }
            [((ScrubberItemView *) view) setEnabled:enabled];
        }
        free(indices);
    });
}

// NOTE: called from EDT (when update UI)
void showScrubberItems(id scrubObj, void* itemIndices, int count, bool show) {
    NSScrubberContainer * container = scrubObj;
    NSScrubber *scrubber = container.view;

    const int sizeInBytes = sizeof(int)*count;
    int *indices = malloc(sizeInBytes);
    memcpy(indices, itemIndices, sizeInBytes);

    dispatch_async(dispatch_get_main_queue(), ^{
        // 1. mark items
        for (int c = 0; c < count; ++c) {
            ScrubberItem *itemData = [container.itemsCache objectAtIndex:indices[c]];
            if (itemData == nil) {
                nserror(@"scrubber [%@]: called showScrubberItems %d, but item-data at this index is null", container.identifier, indices[c]);
                continue;
            }
            itemData.visible = show;
        }

        // 2. recalculate positions
        NSMutableIndexSet *indexSet = [[[NSMutableIndexSet alloc] init] autorelease];
        [container.visibleItems removeAllObjects];
        int position = 0;
        for (int c = 0; c < container.itemsCache.count; ++c) {
            const ScrubberItem * si = [container.itemsCache objectAtIndex:c];
            if (si == nil)
                continue;
            if (si.visible) {
                if (si.positionInsideScrubber < 0) {
                    // item is visible now => insert into scrubber
                    [indexSet addIndex:position];
                }
                si.positionInsideScrubber = position++;
                [container.visibleItems addObject:si];
            } else {
                if (si.positionInsideScrubber >= 0) {
                    // item is hidden now => remove from scrubber
                    [indexSet addIndex:si.positionInsideScrubber];
                    si.positionInsideScrubber = -1;
                }
            }
        }

        if (show) {
            nstrace(@"\t show %d items", [indexSet count]);
            [scrubber insertItemsAtIndexes:indexSet];
        } else {
            nstrace(@"\t hide %d items", [indexSet count]);
            [scrubber removeItemsAtIndexes:indexSet];
        }

        free(indices);
    });
}
