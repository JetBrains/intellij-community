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

static void _fillCache(NSMutableArray * cache, NSMutableArray * visibleItems, void* items, int byteCount, int fromIndex) {
    if (cache == NULL || visibleItems == NULL || items == NULL || byteCount <= 0)
        return;
    const int prevCacheSize = cache.count;
    const char * p = items;
    const int itemsCount = *((short*)p);
    p += 2;
    nstrace(@"================  items count = %d, bytes = %d, from index = %d ================", itemsCount, byteCount, fromIndex);
    for (int c = 0; c < itemsCount; ++c) {
        const int txtLen = *((short*)p);
        p += 2;
        NSString * txt = txtLen == 0 ? nil : [NSString stringWithUTF8String:p];
        if (txtLen > 0)
          p += txtLen + 1;
        //NSLog(@"\t len=%d, txt=%@, offset=%d", txtLen, txt, p - (char*)items);

        const int w = *((short*)p);
        p += 2;
        const int h = *((short*)p);
        p += 2;
        //NSLog(@"\t w=%d, h=%d", w, h);

        int cacheIndex = fromIndex + c;

        NSImage * img = w <= 0 || h <= 0 ? nil : createImgFrom4ByteRGBA((const unsigned char *)p, w, h);

        if (cacheIndex >= cache.count) {
          // Add new cache-item
          nstrace(@"\t add new item, cacheIndex=%d", cacheIndex);
          ScrubberItem * si = [[[ScrubberItem alloc] init] autorelease];
          si.index = cacheIndex;
          si.visible = true;
          si.enabled = true;
          si.text = txt;
          si.img = img;
          if (img != nil || txt != nil) {
            si.positionInsideScrubber = visibleItems.count;
            [visibleItems addObject:si];
          } else {
            si.positionInsideScrubber = -1;
          }
          [cache addObject:si];
        } else {
          // Just update image and text
          nstrace(@"\t update item at index %d", cacheIndex);
          ScrubberItem * si = [cache objectAtIndex:cacheIndex];
          si.text = txt;
          si.img = img;
        }

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

    _fillCache(scrubberItem.itemsCache, scrubberItem.visibleItems, packedItems, byteCount, 0);

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

void _recalculatePositions(id scrubObj);

// NOTE: called from AppKit (when show last cached item and need update cache with new items)
void updateScrubberItems(id scrubObj, void* packedItems, int byteCount, int fromIndex) {
    NSScrubberContainer *container = scrubObj;
    nstrace(@"scrubber [%@]: called updateScrubberItems", container.identifier);
    _fillCache(container.itemsCache, container.visibleItems, packedItems, byteCount, fromIndex);
    _recalculatePositions(scrubObj);
}

// NOTE: called from EDT (when update UI)
void enableScrubberItems(id scrubObj, void* itemIndices, int count, bool enabled) {
    if (itemIndices == NULL || count <= 0)
        return;

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
void showScrubberItems(id scrubObj, void* itemIndices, int count, bool show, bool inverseOthers) {
    NSScrubberContainer * container = scrubObj;
    NSScrubber *scrubber = container.view;

    int * indices = NULL;
    if (itemIndices != NULL && count > 0) {
        const int sizeInBytes = sizeof(int)*count;
        indices = malloc(sizeInBytes);
        memcpy(indices, itemIndices, sizeInBytes);
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        // 1. mark items
        if (inverseOthers) {
            for (int c = 0; c < container.itemsCache.count; ++c) {
                ScrubberItem *itemData = [container.itemsCache objectAtIndex:c];
                if (itemData == nil)
                    continue;
                itemData.visible = !show;
            }
        }

        if (indices != NULL) {
            for (int c = 0; c < count; ++c) {
                ScrubberItem *itemData = [container.itemsCache objectAtIndex:indices[c]];
                if (itemData == nil) {
                    nserror(@"scrubber [%@]: called showScrubberItems %d, but item-data at this index is null", container.identifier, indices[c]);
                    continue;
                }
                itemData.visible = show;
            }
        }

        // 2. recalc positions
        _recalculatePositions(scrubObj);

        if (indices != NULL)
            free(indices);
    });
}

void _recalculatePositions(id scrubObj) {
    NSScrubberContainer * container = scrubObj;
    NSScrubber *scrubber = container.view;

    NSMutableIndexSet *visibleIndexSet = [[[NSMutableIndexSet alloc] init] autorelease];
    NSMutableIndexSet *hiddenIndexSet = [[[NSMutableIndexSet alloc] init] autorelease];
    NSMutableArray *newVisibleItems = [[[NSMutableArray alloc] initWithCapacity:10/*empiric average popup items count*/] autorelease];
    int position = 0;
    int prevPosition = -1;
    bool insertContinuousChunk = true;
    for (int c = 0; c < container.itemsCache.count; ++c) {
        const ScrubberItem * si = [container.itemsCache objectAtIndex:c];
        if (si == nil)
            continue;
        if (si.visible && (si.img != nil || si.text != nil)) { // item is visible and has been loaded
            if (si.positionInsideScrubber < 0) {
                // item is visible now => insert into scrubber
                [visibleIndexSet addIndex:position];
                if (prevPosition >= 0 && position != prevPosition + 1)
                    insertContinuousChunk = false;
                prevPosition = position;
            }
            si.positionInsideScrubber = position++;
            [newVisibleItems addObject:si];
        } else {
            if (si.positionInsideScrubber >= 0) {
                // item is hidden now => remove from scrubber
                [hiddenIndexSet addIndex:si.positionInsideScrubber];
                si.positionInsideScrubber = -1;
            }
        }
    }

    if ([hiddenIndexSet count] > 0 || [visibleIndexSet count] > 0) {
        if ([hiddenIndexSet count] == 0) {
            const int prevVisibleCount = container.visibleItems.count;
            container.visibleItems = newVisibleItems;
            [scrubber insertItemsAtIndexes:visibleIndexSet];

            if (!insertContinuousChunk || [visibleIndexSet lastIndex] <= prevVisibleCount) { // empiric rule: can omit 'reloadData' if items were just appended (otherwise it will crash with strange stacktrace)
                [scrubber reloadData];
            }
        } else {
            [scrubber performSequentialBatchUpdates:^{
                [scrubber removeItemsAtIndexes:hiddenIndexSet];
                if ([visibleIndexSet count] > 0) {
                    [scrubber insertItemsAtIndexes:visibleIndexSet];
                }
                container.visibleItems = newVisibleItems;
            }];

            [scrubber reloadData]; // empiric rule: need to call reload if some items were removed (otherwise it will crash with strange stacktrace)
        }
    }
}